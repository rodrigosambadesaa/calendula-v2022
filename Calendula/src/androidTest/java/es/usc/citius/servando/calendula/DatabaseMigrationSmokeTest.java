/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.DatabaseHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Runs historical database upgrades against a temporary private SQLite file,
 * NEVER the installed application's medical database. Exercises actual device
 * SQLiteOpenHelper transactions on Android 6, 13 and 16.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationSmokeTest {

    private static final class Fixture {
        final File folder;
        final File databaseFile;
        final Context context;

        Fixture(String name) {
            Context real = InstrumentationRegistry.getInstrumentation().getTargetContext();
            folder = new File(real.getCacheDir(), name + "-" + System.nanoTime());
            if (!folder.mkdirs()) {
                throw new IllegalStateException("Cannot create isolated test database folder");
            }
            databaseFile = new File(folder, DB.DB_NAME);
            context = new ContextWrapper(real) {
                @Override
                public File getDatabasePath(String databaseName) {
                    return new File(folder, databaseName);
                }

                @Override
                public SQLiteDatabase openOrCreateDatabase(
                        String databaseName, int mode, SQLiteDatabase.CursorFactory factory) {
                    return SQLiteDatabase.openOrCreateDatabase(
                            getDatabasePath(databaseName), factory);
                }

                @Override
                public SQLiteDatabase openOrCreateDatabase(
                        String databaseName, int mode, SQLiteDatabase.CursorFactory factory,
                        DatabaseErrorHandler errorHandler) {
                    return SQLiteDatabase.openOrCreateDatabase(
                            getDatabasePath(databaseName).getAbsolutePath(),
                            factory, errorHandler);
                }
            };
        }

        void seedVersionOne(boolean omitHtmlCache) {
            try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(
                    databaseFile, null)) {
                old.execSQL("CREATE TABLE Patients (_id INTEGER PRIMARY KEY,"
                        + " Code VARCHAR, Name VARCHAR)");
                old.execSQL("INSERT INTO Patients (Code, Name) VALUES"
                        + " ('synthetic-patient', 'Existing patient')");
                old.execSQL("CREATE TABLE ActiveMedItems (_id INTEGER PRIMARY KEY,"
                        + " Code VARCHAR, DefaultDisplay VARCHAR)");
                old.execSQL("INSERT INTO ActiveMedItems (Code, DefaultDisplay) VALUES"
                        + " ('synthetic-med', 'Existing medication')");
                if (!omitHtmlCache) {
                    old.execSQL("CREATE TABLE HtmlCache (_id INTEGER PRIMARY KEY,"
                            + " HashCode INTEGER, Data VARCHAR)");
                }
                old.setVersion(1);
            }
        }

        void cleanup() {
            // All handles are closed before deletion. These are synthetic
            // private-cache files only, never app data under databases/.
            for (String suffix : new String[]{"", "-wal", "-shm", "-journal"}) {
                File file = new File(databaseFile.getAbsolutePath() + suffix);
                if (file.exists() && !file.delete()) {
                    throw new IllegalStateException(
                            "Could not remove synthetic database fixture");
                }
            }
            if (!folder.delete()) {
                throw new IllegalStateException(
                        "Could not remove synthetic database fixture directory");
            }
        }
    }

    private static void assertSavedRecords(SQLiteDatabase database) {
        try (Cursor patient = database.rawQuery(
                "SELECT Name FROM Patients WHERE Code=?",
                new String[]{"synthetic-patient"})) {
            assertTrue(patient.moveToFirst());
            assertEquals("Existing patient", patient.getString(0));
        }
        try (Cursor medicine = database.rawQuery(
                "SELECT DefaultDisplay FROM ActiveMedItems WHERE Code=?",
                new String[]{"synthetic-med"})) {
            assertTrue(medicine.moveToFirst());
            assertEquals("Existing medication", medicine.getString(0));
        }
    }

    private static boolean hasColumn(SQLiteDatabase database,
                                     String table, String column) {
        // Tables are constants in this test, never user-supplied identifiers.
        try (Cursor columns = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int columnName = columns.getColumnIndexOrThrow("name");
            while (columns.moveToNext()) {
                if (column.equals(columns.getString(columnName))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void v1UpgradePreservesRecordsOnRealDevice() {
        Fixture fixture = new Fixture("successful-legacy-migration");
        try {
            fixture.seedVersionOne(false);
            DatabaseHelper helper = new DatabaseHelper(fixture.context);
            try {
                SQLiteDatabase upgraded = helper.getWritableDatabase();
                assertEquals(DatabaseHelper.DATABASE_VERSION, upgraded.getVersion());
                assertSavedRecords(upgraded);
                assertTrue(hasColumn(upgraded, "ActiveMedItems", "VisualizationType"));
                assertTrue(hasColumn(upgraded, "HtmlCache", "Url"));
            } finally {
                helper.close();
            }
        } finally {
            fixture.cleanup();
        }
    }

    @Test
    public void failedV1UpgradeRollsBackWithoutDeletingRecordsOnRealDevice() {
        Fixture fixture = new Fixture("rolled-back-legacy-migration");
        try {
            fixture.seedVersionOne(true);
            DatabaseHelper helper = new DatabaseHelper(fixture.context);
            try {
                try {
                    helper.getWritableDatabase();
                    fail("Missing HtmlCache must abort the upgrade transaction");
                } catch (RuntimeException expected) {
                    // Intentionally missing second migration table.
                }
            } finally {
                helper.close();
            }

            try (SQLiteDatabase unchanged = SQLiteDatabase.openDatabase(
                    fixture.databaseFile.getAbsolutePath(),
                    null, SQLiteDatabase.OPEN_READONLY)) {
                assertEquals(1, unchanged.getVersion());
                assertSavedRecords(unchanged);
                assertFalse(hasColumn(unchanged, "ActiveMedItems", "VisualizationType"));
            }
        } finally {
            fixture.cleanup();
        }
    }
}
