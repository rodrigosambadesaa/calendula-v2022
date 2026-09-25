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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HtmlCacheManagerTest {

    private static final String COLLIDING_URL_A = "https://example.test/Aa";
    private static final String COLLIDING_URL_B = "https://example.test/BB";

    @Before
    public void setUp() {
        CalendulaApp.disableReceivers = true;
        DB.init(ApplicationProvider.getApplicationContext());
        DB.dropAndCreateDatabase();
    }

    @Test
    public void javaHashCollisionNeverReturnsAnotherUrlsHtml() {
        assertEquals(COLLIDING_URL_A.hashCode(), COLLIDING_URL_B.hashCode());

        HtmlCacheManager cache = HtmlCacheManager.getInstance();
        cache.put(COLLIDING_URL_A, "<html>A</html>", Duration.standardMinutes(5));

        assertEquals("<html>A</html>", cache.get(COLLIDING_URL_A));
        assertNull(cache.get(COLLIDING_URL_B));

        cache.put(COLLIDING_URL_B, "<html>B</html>", Duration.standardMinutes(5));

        assertNull(cache.get(COLLIDING_URL_A));
        assertEquals("<html>B</html>", cache.get(COLLIDING_URL_B));
    }
}
