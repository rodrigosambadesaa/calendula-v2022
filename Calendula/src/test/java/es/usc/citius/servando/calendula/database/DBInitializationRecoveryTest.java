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
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DBInitializationRecoveryTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void failedDatabaseOpenIsNotReportedReadyAndCanBeRetried() throws IOException {
        if (DB.initialized) {
            DB.dispose();
        }
        Context isolated = new ContextWrapper(
                ApplicationProvider.getApplicationContext()) {
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
        FailFirstManager manager = new FailFirstManager();

        try {
            try {
                DB.init(isolated, manager);
                fail("Synthetic helper failure must fail initialization");
            } catch (IllegalStateException expected) {
                // Invariant: no cached helper or ready flag may survive.
            }
            assertFalse("Failed initialization must remain retryable", DB.initialized);
            assertNull("Failed helper must not be published", DB.helper());

            // Retry with the same manager, context and disposable database path.
            DB.init(isolated, manager);
            assertTrue(DB.initialized);
            assertNotNull(DB.helper());
            assertNotNull(DB.patients());
        } finally {
            if (DB.initialized) {
                DB.dispose();
            }
        }
    }

    private static final class FailFirstManager extends DatabaseManager<DatabaseHelper> {
        private boolean throwOnce = true;

        @Override
        public synchronized DatabaseHelper getHelper(
                Context context, Class<DatabaseHelper> helperType) {
            if (throwOnce) {
                throwOnce = false;
                throw new IllegalStateException("Synthetic helper acquisition failure");
            }
            return super.getHelper(context, helperType);
        }
    }
}
