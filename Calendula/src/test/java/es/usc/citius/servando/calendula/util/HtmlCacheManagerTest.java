/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.joda.time.Duration;

import com.j256.ormlite.dao.Dao;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Date;

import es.usc.citius.servando.calendula.persistence.HtmlCacheEntry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.database.DB;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HtmlCacheManagerTest {

    private static final String COLLIDING_URL_A = "https://example.test/Aa";
    private static final String COLLIDING_URL_B = "https://example.test/BB";

    @Before
    public void setUp() {
        CalendulaApp.disableReceivers = true;
        // Robolectric creates a fresh SQLite environment for each method.
        // Release the old helper so the cached DAO cannot point to a closed DB.
        if (DB.initialized) {
            DB.dispose();
        }
        DB.init(ApplicationProvider.getApplicationContext());
        DB.dropAndCreateDatabase();
    }

    @Test
    public void javaHashCollisionNeverReturnsAnotherUrlsHtml() {
        assertEquals(COLLIDING_URL_A.hashCode(), COLLIDING_URL_B.hashCode());

        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        assertTrue(cache.put(COLLIDING_URL_A, "<html>A</html>", Duration.standardMinutes(5)));

        assertEquals("<html>A</html>", cache.get(COLLIDING_URL_A));
        assertNull(cache.get(COLLIDING_URL_B));

        assertFalse("Colliding URL must not evict another cached medicine document",
                cache.put(COLLIDING_URL_B, "<html>B</html>", Duration.standardMinutes(5)));
        assertEquals("The existing URL must retain its original cached HTML",
                "<html>A</html>", cache.get(COLLIDING_URL_A));
        assertNull("The collision cannot return another URL's document",
                cache.get(COLLIDING_URL_B));
    }

    @Test
    public void refreshingSameUrlReplacesHtmlWithoutDeletingCacheRecord() {
        String url = "https://example.test/synthetic-prescription";
        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        assertTrue(cache.put(url, "Original synthetic leaflet", Duration.standardMinutes(5)));
        assertEquals("Original synthetic leaflet", cache.get(url));

        boolean updated = cache.put(url, "Updated synthetic leaflet", Duration.standardMinutes(5));
        assertTrue("Cache refresh must succeed; persisted value=" + cache.get(url),
                updated);
        assertEquals("Updated synthetic leaflet", cache.get(url));
    }

    @Test
    public void sqliteRefreshFailureKeepsPreviouslyCommittedHtml() {
        String url = "https://example.test/failure-injected-leaflet";
        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        assertTrue(cache.put(url, "Original synthetic leaflet", Duration.standardMinutes(5)));
        android.database.sqlite.SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        String trigger = "ci_test_html_cache_update_failure";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON HtmlCache"
                    + " WHEN OLD.HashCode = " + url.hashCode()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic cache refresh failure'); END;");
            assertFalse("Injected SQL error must not erase the previous HTML",
                    cache.put(url, "Incomplete replacement", Duration.standardMinutes(5)));
            assertEquals("Original leaflet must remain readable after failed refresh",
                    "Original synthetic leaflet", cache.get(url));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        }
    }

    @Test
    public void purgeCountsOnlyRemovedExpiredRowsAndPreservesValidDocuments() {
        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        String validUrl = "https://example.test/synthetic-valid-leaflet";
        String expiredUrl = "https://example.test/synthetic-expired-leaflet";

        assertTrue(cache.put(validUrl, "Still valid synthetic content",
                Duration.standardMinutes(30)));
        assertTrue(cache.put(expiredUrl, "Expired synthetic content",
                Duration.millis(-1)));

        assertEquals("Exactly one expired row is removed, not two scanned rows",
                Integer.valueOf(1), cache.purgeCache());
        assertEquals("Still valid synthetic content", cache.get(validUrl));
        assertNull(cache.get(expiredUrl));
        assertEquals("Second cleanup should find nothing to delete",
                Integer.valueOf(0), cache.purgeCache());
    }

    @Test
    public void purgeHandlesIncompleteLegacyCacheMetadataWithoutDeletingValidRows()
            throws Exception {
        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        String validUrl = "https://example.test/synthetic-retained-document";
        assertTrue(cache.put(validUrl, "Healthy synthetic content",
                Duration.standardMinutes(30)));

        String missingDateUrl = "https://example.test/synthetic-missing-date";
        String missingTtlUrl = "https://example.test/synthetic-missing-ttl";
        com.j256.ormlite.dao.Dao<es.usc.citius.servando.calendula.persistence.HtmlCacheEntry, Long> dao =
                DB.helper().getDao(es.usc.citius.servando.calendula.persistence.HtmlCacheEntry.class);
        dao.create(new es.usc.citius.servando.calendula.persistence.HtmlCacheEntry(
                missingDateUrl.hashCode(), missingDateUrl, null, "Malformed date", 60000L));
        dao.create(new es.usc.citius.servando.calendula.persistence.HtmlCacheEntry(
                missingTtlUrl.hashCode(), missingTtlUrl, new java.util.Date(),
                "Malformed TTL", null));

        assertEquals(Integer.valueOf(2), cache.purgeCache());
        assertEquals("Healthy synthetic content", cache.get(validUrl));
        assertNull(cache.get(missingDateUrl));
        assertNull(cache.get(missingTtlUrl));
        assertEquals(Integer.valueOf(0), cache.purgeCache());
    }

    @Test
    public void invalidOrOversizedHtmlCannotOverwritePriorLeaflet() {
        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        String url = "https://example.test/previously-good-patient-leaflet";
        assertTrue(cache.put(url, "Safe old leaflet", Duration.standardMinutes(10)));

        assertFalse(cache.put(url, null, Duration.standardMinutes(10)));
        assertFalse(cache.put(url, "", Duration.standardMinutes(10)));
        String oversized = new String(new char[HtmlCacheManager.MAX_CACHED_HTML_CHARS + 1])
                .replace((char) 0, 'x');
        assertFalse(cache.put(url, oversized, Duration.standardMinutes(10)));
        assertEquals("A bad download cannot evict the previously committed document",
                "Safe old leaflet", cache.get(url));
    }

    @Test
    public void enormousLegacyCacheRowsAreExpiredInsteadOfDisplayed() throws Exception {
        String url = "https://example.test/large-imported-cached-html";
        String oversized = new String(new char[HtmlCacheManager.MAX_CACHED_HTML_CHARS + 1])
                .replace((char) 0, 'x');
        com.j256.ormlite.dao.Dao<es.usc.citius.servando.calendula.persistence.HtmlCacheEntry, Long> dao =
                DB.helper().getDao(es.usc.citius.servando.calendula.persistence.HtmlCacheEntry.class);
        dao.create(new es.usc.citius.servando.calendula.persistence.HtmlCacheEntry(
                url.hashCode(), url, new java.util.Date(), oversized,
                Duration.standardMinutes(10).getMillis()));

        assertNull(HtmlCacheManager.getInstance().get(url));
        assertNull("The oversized row is discarded rather than rendered",
                HtmlCacheManager.getInstance().get(url));
    }

    @Test
    public void identicalLeafletUrlIsolatedForTwoPatientsAndNeverReusesLegacyCache() {
        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        String url = "https://example.test/same-leaflet-for-two-patients";
        org.joda.time.Duration ttl = org.joda.time.Duration.standardMinutes(10);
        assertTrue(cache.put(url, "Legacy unscoped leaflet", ttl));
        assertNull("Historical entries have no trusted patient owner",
                cache.getForPatient(20011L, url));
        assertFalse(cache.isCachedForPatient(20011L, url));

        assertTrue(cache.putForPatient(20011L, url, "Patient one leaflet", ttl));
        assertTrue(cache.putForPatient(20012L, url, "Patient two leaflet", ttl));
        assertEquals("Patient one leaflet", cache.getForPatient(20011L, url));
        assertEquals("Patient two leaflet", cache.getForPatient(20012L, url));
        assertEquals("Legacy unscoped leaflet", cache.get(url));

        assertNull(cache.getForPatient(20013L, url));
        assertFalse(cache.isCachedForPatient(-1L, url));
        assertNull(cache.getForPatient(null, url));
        assertFalse(cache.putForPatient(null, url, "Unowned clinical content", ttl));
    }

    @Test
    public void restoredPatientIdCannotReadOldPatientsCachedMedicalHtml() {
        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        String url = "https://example.test/synthetic-sensitive-leaflet";
        Duration ttl = Duration.standardMinutes(10);
        long reusedId = 20011L;

        // Both old unscoped and ID-only v1 entries must be invisible to the
        // clinical viewer using stable-code-scoped v2 cache keys.
        assertTrue(cache.put(url, "Unscoped legacy document", ttl));
        assertTrue(cache.putForPatient(reusedId, url, "Legacy ID-only document", ttl));
        assertNull(cache.getForPatient(reusedId, "synthetic-a", url));
        assertFalse(cache.isCachedForPatient(reusedId, "synthetic-a", url));

        assertTrue(cache.putForPatient(reusedId, "synthetic-a", url,
                "Patient A private leaflet", ttl));
        assertEquals("Patient A private leaflet",
                cache.getForPatient(reusedId, "synthetic-a", url));
        assertNull("Reused numeric ID must not authorize patient A's HTML",
                cache.getForPatient(reusedId, "synthetic-b", url));
        assertTrue(cache.putForPatient(reusedId, "synthetic-b", url,
                "Patient B private leaflet", ttl));
        assertEquals("Patient B private leaflet",
                cache.getForPatient(reusedId, "synthetic-b", url));
        assertEquals("Patient A private leaflet",
                cache.getForPatient(reusedId, "synthetic-a", url));
        assertNull(cache.getForPatient(reusedId + 1, "synthetic-a", url));

        String v2Key = HtmlCacheManager.patientCacheKey(reusedId, "synthetic-a", url);
        assertFalse("Raw stable clinical code must not be persisted in the key",
                v2Key.contains("synthetic-a"));
        assertTrue(v2Key.startsWith("patient:v2:"));
        assertFalse(cache.putForPatient(reusedId, null, url, "Unowned HTML", ttl));
        assertNull(cache.getForPatient(reusedId, " ", url));
    }

    @Test
    public void malformedScopedPatientKeysAreRejectedBeforeSql() {
        assertNull(HtmlCacheManager.patientCacheKey(null, "https://example.test/leaflet"));
        assertNull(HtmlCacheManager.patientCacheKey(0L, "https://example.test/leaflet"));
        assertNull(HtmlCacheManager.patientCacheKey(-5L, "https://example.test/leaflet"));
        assertNull(HtmlCacheManager.patientCacheKey(7L, null));
        assertNull(HtmlCacheManager.patientCacheKey(7L, ""));
    }

    @Test
    public void unknownLegacyCacheIdentityIsNeitherDeletedNorAdoptedOnReadOrWrite()
            throws Exception {
        String requested = "https://example.test/synthetic-legacy-unknown-owner";
        String original = "Synthetic historical document with unknown URL";
        Dao<HtmlCacheEntry, Long> dao = DB.helper().getDao(HtmlCacheEntry.class);
        HtmlCacheEntry oldEntry = new HtmlCacheEntry(
                requested.hashCode(), null, new Date(), original,
                Duration.standardMinutes(20).getMillis());
        assertEquals(1, dao.create(oldEntry));
        assertTrue(oldEntry.getId() != null && oldEntry.getId() > 0L);

        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        assertNull("Unknown legacy URL must never be displayed", cache.get(requested));
        assertFalse("Unknown legacy URL must never be reported cached",
                cache.isCached(requested));
        assertFalse("Hash equality alone must not reassign another owner's data",
                cache.put(requested, "New synthetic document",
                        Duration.standardMinutes(20)));

        HtmlCacheEntry persisted = dao.queryForId(oldEntry.getId());
        assertTrue("Read/write must not delete the unidentifiable document",
                persisted != null);
        assertNull("Read/write must not adopt a new URL without identity proof",
                persisted.getUrl());
        assertEquals(original, persisted.getData());
        assertEquals(requested.hashCode(), (int) persisted.getHashCode());
    }

    @Test
    public void missingUrlCannotCreateCacheRow() {
        assertFalse(HtmlCacheManager.getInstance().put(
                null, "Unkeyed synthetic content", Duration.standardMinutes(5)));
    }
    @Test
    @SuppressWarnings("unchecked")
    public void ambiguousLegacyHashNeverErasesMultipleCachedDocuments()
            throws Exception {
        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        String requested = "https://example.test/synthetic-conflicting-cache";
        HtmlCacheEntry first = new HtmlCacheEntry(
                requested.hashCode(), requested, new Date(),
                "Synthetic original leaflet", Duration.standardMinutes(10).getMillis());
        HtmlCacheEntry second = new HtmlCacheEntry(
                requested.hashCode(), "https://example.test/synthetic-unrelated-cache",
                new Date(), "Synthetic unrelated leaflet",
                Duration.standardMinutes(10).getMillis());

        // The production schema has a unique index. Inject an inconsistent
        // legacy DAO result without corrupting or altering the SQLite schema.
        Dao<HtmlCacheEntry, Long> injectedDao = mock(Dao.class);
        when(injectedDao.queryForEq(HtmlCacheEntry.COLUMN_HASHCODE, requested.hashCode()))
                .thenReturn(Arrays.asList(first, second));
        Field daoField = HtmlCacheManager.class.getDeclaredField("dao");
        Field helperField = HtmlCacheManager.class.getDeclaredField("daoHelper");
        daoField.setAccessible(true);
        helperField.setAccessible(true);
        Object previousDao = daoField.get(cache);
        Object previousHelper = helperField.get(cache);
        try {
            daoField.set(cache, injectedDao);
            helperField.set(cache, DB.helper());

            assertNull("Ambiguous cache lookup must never return patient content",
                    cache.get(requested));
            assertFalse(cache.isCached(requested));
            verify(injectedDao, never()).delete(any(HtmlCacheEntry.class));
        } finally {
            daoField.set(cache, previousDao);
            helperField.set(cache, previousHelper);
        }
    }

}
