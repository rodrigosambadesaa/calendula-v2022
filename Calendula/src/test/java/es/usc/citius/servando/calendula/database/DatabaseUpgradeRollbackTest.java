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
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DatabaseUpgradeRollbackTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void secondMigrationFailureRollsBackFirstAlterAndPreservesPatientData()
            throws Exception {
        File sandbox = temporaryFolder.newFolder("failed-migration");
        Context isolated = new ContextWrapper(ApplicationProvider.getApplicationContext()) {
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

        File databaseFile = isolated.getDatabasePath(DB.DB_NAME);
        try (SQLiteDatabase legacy =
                     SQLiteDatabase.openOrCreateDatabase(databaseFile, null)) {
            legacy.execSQL("CREATE TABLE Patients ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "Code VARCHAR, Name VARCHAR)");
            legacy.execSQL("INSERT INTO Patients(Code,Name) VALUES"
                    + " ('synthetic-patient','Important existing record')");
            legacy.execSQL("CREATE TABLE ActiveMedItems ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "Code VARCHAR, DefaultDisplay VARCHAR)");
            legacy.execSQL("INSERT INTO ActiveMedItems(Code,DefaultDisplay) VALUES"
                    + " ('synthetic-cn','Important medication')");
            // Deliberately omit HtmlCache: the v1->v2 ALTER succeeds, but
            // the next (v2->v3) ALTER fails inside SQLiteOpenHelper's
            // schema-upgrade transaction.
            legacy.setVersion(1);
        }

        DatabaseHelper helper = new DatabaseHelper(isolated);
        try {
            try {
                helper.getWritableDatabase();
                fail("Missing HtmlCache must abort this synthetic upgrade");
            } catch (RuntimeException expected) {
                // Never allow a failed ALTER to rebuild/drop the user's tables.
            }
        } finally {
            helper.close();
        }

        try (SQLiteDatabase unchanged = SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            assertEquals("Failed upgrade must preserve original schema version",
                    1, unchanged.getVersion());
            try (Cursor patient = unchanged.rawQuery(
                    "SELECT Name FROM Patients WHERE Code = ?",
                    new String[]{"synthetic-patient"})) {
                assertTrue("Patient must survive rollback", patient.moveToFirst());
                assertEquals("Important existing record", patient.getString(0));
            }
            try (Cursor medication = unchanged.rawQuery(
                    "SELECT DefaultDisplay FROM ActiveMedItems WHERE Code = ?",
                    new String[]{"synthetic-cn"})) {
                assertTrue("Medication must survive rollback", medication.moveToFirst());
                assertEquals("Important medication", medication.getString(0));
            }

            boolean firstAlterPersisted = false;
            try (Cursor schema = unchanged.rawQuery(
                    "PRAGMA table_info(ActiveMedItems)", null)) {
                int nameIndex = schema.getColumnIndexOrThrow("name");
                while (schema.moveToNext()) {
                    if ("VisualizationType".equals(schema.getString(nameIndex))) {
                        firstAlterPersisted = true;
                    }
                }
            }
            assertFalse("Earlier ALTER must be rolled back after later failure",
                    firstAlterPersisted);
        }
    }
}
