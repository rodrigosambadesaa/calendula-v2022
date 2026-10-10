/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Native ART multidex starts without the legacy MultiDexApplication bootstrap.
 * Exercises the actual application classloader on Android 6, 13 and 16.
 */
@RunWith(AndroidJUnit4.class)
public class NativeMultidexSmokeTest {

    @Test
    public void supportedAndroidVersionsLoadApplicationAndDeepFeatureClasses() throws Exception {
        assertTrue("Native multidex requires API 21+", Build.VERSION.SDK_INT >= 23);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Application app = (Application) context.getApplicationContext();
        assertNotNull(app);
        assertEquals(CalendulaApp.class, app.getClass());
        assertEquals("Legacy MultiDexApplication must not be required",
                Application.class, CalendulaApp.class.getSuperclass());
        ClassLoader loader = app.getClassLoader();
        assertNotNull(Class.forName(
                "es.usc.citius.servando.calendula.database.DB", false, loader));
        assertNotNull(Class.forName(
                "es.usc.citius.servando.calendula.drugdb.cima.CimaRestCatalog", false, loader));
        assertNotNull(Class.forName(
                "es.usc.citius.servando.calendula.scheduling.Agenda", false, loader));
    }
}
