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

        assertTrue(cache.put(url, "Updated synthetic leaflet", Duration.standardMinutes(5)));
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
    public void missingUrlCannotCreateCacheRow() {
        assertFalse(HtmlCacheManager.getInstance().put(
                null, "Unkeyed synthetic content", Duration.standardMinutes(5)));
    }
}
