/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DatabaseManagerReleaseTest {

    private static Object cachedHelper(DatabaseManager<?> manager) throws Exception {
        Field field = DatabaseManager.class.getDeclaredField("helper");
        field.setAccessible(true);
        return field.get(manager);
    }

    @Test
    public void releasingManagedHelperClearsCachedReference() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseManager<DatabaseHelper> manager = new DatabaseManager<>();
        DatabaseHelper helper = manager.getHelper(context, DatabaseHelper.class);
        try {
            assertSame(helper, cachedHelper(manager));
        } finally {
            manager.releaseHelper(helper);
        }
        assertNull("Released SQLite helper must not remain cached", cachedHelper(manager));
    }

    @Test
    public void releasingUnmanagedHelperMustNotReleaseManagedReference() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseManager<DatabaseHelper> manager = new DatabaseManager<>();
        DatabaseHelper helper = manager.getHelper(context, DatabaseHelper.class);
        DatabaseHelper unrelated = new DatabaseHelper(context);
        try {
            manager.releaseHelper(unrelated);
            assertSame("Only this manager's helper can be released",
                    helper, cachedHelper(manager));
        } finally {
            manager.releaseHelper(helper);
            unrelated.close();
        }
        assertNull(cachedHelper(manager));
    }
}
