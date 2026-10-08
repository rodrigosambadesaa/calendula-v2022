/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LegacyV1MigrationFixtureTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void legacyVersionOneUpgradeAddsBothColumnsWithoutLosingPatient() throws Exception {
        File sandbox = temp.newFolder("legacy-v1");
        Context base = ApplicationProvider.getApplicationContext();
        Context isolated = new ContextWrapper(base) {
            @Override
            public File getDatabasePath(String name) {
                return new File(sandbox, name);
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

        File dbFile = isolated.getDatabasePath(DB.DB_NAME);
        // Create a minimal, synthetic pre-v2 schema. Do not access the user's
        // real database or rely on medical data from actual installations.
        try (SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
            legacy.execSQL("CREATE TABLE Patients ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "Code VARCHAR, Name VARCHAR, "
                    + "`Default` BOOLEAN, DataRetrieved BOOLEAN, "
                    + "Avatar VARCHAR, Color INTEGER)");
            legacy.execSQL("INSERT INTO Patients (Code, Name) VALUES "
                    + "('synthetic-before-upgrade', 'Saved patient')");
            legacy.execSQL("CREATE TABLE HtmlCache ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "HashCode INTEGER, Timestamp VARCHAR, "
                    + "Data VARCHAR, ttl BIGINT)");
            // v1 did not yet have VisualizationType on ActiveMedItems.
            legacy.execSQL("CREATE TABLE ActiveMedItems ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "Patient INTEGER, Type VARCHAR, "
                    + "Code VARCHAR, DefaultDisplay VARCHAR)");
            legacy.execSQL("INSERT INTO ActiveMedItems (Patient, Code, DefaultDisplay)"
                    + " VALUES (1, 'legacy-active-med', 'Legacy medicine')");
            legacy.setVersion(1);
        }

        DatabaseHelper helper = new DatabaseHelper(isolated);
        try {
            SQLiteDatabase upgraded = helper.getWritableDatabase();
            assertEquals(DatabaseHelper.DATABASE_VERSION, upgraded.getVersion());

            try (Cursor patient = upgraded.rawQuery(
                    "SELECT Name FROM Patients WHERE Code = ?",
                    new String[]{"synthetic-before-upgrade"})) {
                assertTrue("Existing patient must survive upgrade", patient.moveToFirst());
                assertEquals("Saved patient", patient.getString(0));
            }

            boolean foundUrlColumn = false;
            try (Cursor schema = upgraded.rawQuery("PRAGMA table_info(HtmlCache)", null)) {
                int nameColumn = schema.getColumnIndexOrThrow("name");
                while (schema.moveToNext()) {
                    if ("Url".equals(schema.getString(nameColumn))) {
                        foundUrlColumn = true;
                    }
                }
            }
            assertTrue("v1 -> v3 must add the URL cache column", foundUrlColumn);

            boolean foundVisualizationColumn = false;
            try (Cursor schema = upgraded.rawQuery("PRAGMA table_info(ActiveMedItems)", null)) {
                int nameColumn = schema.getColumnIndexOrThrow("name");
                while (schema.moveToNext()) {
                    if ("VisualizationType".equals(schema.getString(nameColumn))) {
                        foundVisualizationColumn = true;
                    }
                }
            }
            assertTrue("v1 -> v3 must add the visualization column",
                    foundVisualizationColumn);

            try (Cursor active = upgraded.rawQuery(
                    "SELECT DefaultDisplay FROM ActiveMedItems WHERE Code = ?",
                    new String[]{"legacy-active-med"})) {
                assertTrue("Existing active medication row must survive", active.moveToFirst());
                assertEquals("Legacy medicine", active.getString(0));
            }
            assertTrue("Existing database file must survive", dbFile.exists());
        } finally {
            helper.close();
        }
    }
}
