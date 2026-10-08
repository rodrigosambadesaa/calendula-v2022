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
        FailingThenWorkingContext isolated = new FailingThenWorkingContext(
                ApplicationProvider.getApplicationContext(),
                temporaryFolder.newFolder("init-recovery"));

        try {
            try {
                DB.init(isolated);
                fail("Synthetic SQLite storage failure must fail initialization");
            } catch (IllegalStateException expected) {
                // An inaccessible database must not be silently considered ready.
            }
            assertFalse("A failed database open must be retryable", DB.initialized);
            assertNull("The failed helper must not be exposed", DB.helper());

            isolated.failOpen = false;
            DB.init(isolated);
            assertTrue("A subsequent valid open must initialize successfully", DB.initialized);
            assertNotNull(DB.helper());
            assertNotNull(DB.patients());
        } finally {
            if (DB.initialized) {
                DB.dispose();
            }
        }
    }

    private static final class FailingThenWorkingContext extends ContextWrapper {
        private final File dbFolder;
        boolean failOpen = true;

        FailingThenWorkingContext(Context base, File dbFolder) {
            super(base);
            this.dbFolder = dbFolder;
        }

        @Override
        public Context getApplicationContext() {
            // Ensure the helper uses this isolated synthetic database, never
            // the real application's database stored in the base context.
            return this;
        }

        @Override
        public File getDatabasePath(String name) {
            return new File(dbFolder, name);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory) {
            if (failOpen) {
                throw new IllegalStateException("Synthetic database storage failure");
            }
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler errorHandler) {
            if (failOpen) {
                throw new IllegalStateException("Synthetic database storage failure");
            }
            return SQLiteDatabase.openOrCreateDatabase(
                    getDatabasePath(name).getAbsolutePath(), factory, errorHandler);
        }
    }
}
