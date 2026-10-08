/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import es.usc.citius.servando.calendula.persistence.Patient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DatabaseUpgradeSafetyTest {

    @Test
    public void invalidLegacyUpgradePreservesPatientInsteadOfRecreatingEveryTable()
            throws SQLException {
        assertExistingPatientSurvivesAnInvalidUpgrade(1);
    }

    @Test
    public void invalidVersionTwoUpgradePreservesPatientInsteadOfDeletingDatabase()
            throws SQLException {
        assertExistingPatientSurvivesAnInvalidUpgrade(2);
    }

    private void assertExistingPatientSurvivesAnInvalidUpgrade(int oldVersion)
            throws SQLException {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper helper = new DatabaseHelper(context);
        Patient sentinel = new Patient();
        sentinel.setName("Synthetic migration sentinel");
        sentinel.setCode("migration-sentinel-" + oldVersion + "-" + System.nanoTime());
        try {
            // Start from the *current* v3 schema, so attempting the legacy
            // ALTER TABLE path deliberately fails with a duplicate column.
            SQLiteDatabase database = helper.getWritableDatabase();
            helper.getPatientDao().create(sentinel);
            assertNotNull(sentinel.getId());

            try {
                helper.onUpgrade(database, helper.getConnectionSource(),
                        oldVersion, DatabaseHelper.DATABASE_VERSION);
                fail("A failed migration must propagate its error");
            } catch (IllegalStateException expected) {
                // A failed migration must never trigger destructive recreation.
            }

            Patient after = helper.getPatientDao().queryForId(sentinel.getId());
            assertNotNull("Previously stored patient must still exist", after);
            assertEquals(sentinel.getName(), after.getName());
        } finally {
            if (sentinel.getId() != null) {
                helper.getPatientDao().deleteById(sentinel.getId());
            }
            helper.close();
        }
    }
}
