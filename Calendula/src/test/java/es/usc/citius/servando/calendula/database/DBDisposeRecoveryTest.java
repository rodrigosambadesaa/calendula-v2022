/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Uses disposable Robolectric SQLite files; never opens an installed user's
 * medical database or changes any production patient records.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DBDisposeRecoveryTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context isolatedContext() {
        return new ContextWrapper(ApplicationProvider.getApplicationContext()) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public File getDatabasePath(String name) {
                return new File(temporaryFolder.getRoot(), name);
            }

            @Override
            public SQLiteDatabase openOrCreateDatabase(
                    String name, int mode, SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
            }

            @Override
            public SQLiteDatabase openOrCreateDatabase(
                    String name, int mode, SQLiteDatabase.CursorFactory factory,
                    DatabaseErrorHandler errorHandler) {
                return SQLiteDatabase.openOrCreateDatabase(
                        getDatabasePath(name).getAbsolutePath(), factory, errorHandler);
            }
        };
    }

    @Test
    public void disposeClearsDaosCanRunTwiceAndAllowsInitializationAgain() {
        Context context = isolatedContext();
        if (DB.initialized) {
            DB.dispose();
        }
        try {
            DB.init(context);
            assertTrue(DB.initialized);
            assertNotNull(DB.helper());
            assertNotNull(DB.patients());
            assertNotNull(DB.medicines());

            DB.dispose();
            assertFalse(DB.initialized);
            assertNull(DB.helper());
            assertNull(DB.patients());
            assertNull(DB.medicines());
            assertNull(DB.schedules());
            assertNull(DB.alerts());
            assertNull(DB.healthcareProviderDB());

            // Previously this second call dereferenced the closed/null helper.
            DB.dispose();
            assertFalse(DB.initialized);
            assertNull(DB.helper());

            DB.init(context);
            assertTrue(DB.initialized);
            assertNotNull(DB.helper());
            assertNotNull(DB.patients());
        } finally {
            DB.dispose();
        }
    }
}
