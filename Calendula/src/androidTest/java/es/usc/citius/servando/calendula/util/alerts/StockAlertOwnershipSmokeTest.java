/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.alerts.StockRunningOutAlert;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.*;

/** Synthetic owner-fixture test; never touches existing patient medicine rows. */
@RunWith(AndroidJUnit4.class)
public class StockAlertOwnershipSmokeTest {

    @Test
    public void staleOrCrossPatientStockUpdatesCannotDeleteAnotherOwnersAlert() throws Exception {
        assertTrue(DB.initialized);
        Patient owner = new Patient();
        owner.setCode("ci-alert-owner-" + System.nanoTime());
        owner.setName("Synthetic stock owner");
        Patient stranger = new Patient();
        stranger.setCode("ci-alert-stranger-" + System.nanoTime());
        stranger.setName("Synthetic other patient");
        Medicine medicine = new Medicine("Synthetic owner medicine", Presentation.PILLS);
        medicine.setStock(4f);
        StockRunningOutAlert alert = null;
        final String prefKey = PreferenceKeys.SETTINGS_STOCK_ALERT_DAYS.key();
        final String oldPref = PreferenceUtils.getString(
                PreferenceKeys.SETTINGS_STOCK_ALERT_DAYS, null);
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(stranger));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            assertNotNull(medicine.getId());
            alert = new StockRunningOutAlert(medicine, LocalDate.now());
            assertEquals(1, DB.alerts().create(alert));
            assertNotNull(alert.id());

            // Disable warnings to exercise the destructive cleanup branch
            // without requiring any real medication schedules.
            assertTrue(PreferenceUtils.edit().putString(prefKey, "-1").commit());

            Medicine impostor = new Medicine("Wrong owner", Presentation.PILLS);
            impostor.setId(medicine.getId());
            impostor.setPatient(stranger);
            impostor.setStock(4f);
            StockAlertHandler.checkStockAlerts(impostor);
            assertNotNull("Cross-patient input must not delete an owner's alert",
                    DB.alerts().findById(alert.id()));

            Medicine stale = new Medicine("Old stock", Presentation.PILLS);
            stale.setId(medicine.getId());
            stale.setPatient(owner);
            stale.setStock(5f);
            StockAlertHandler.checkStockAlerts(stale);
            assertNotNull("A stale amount must not delete an owner's alert",
                    DB.alerts().findById(alert.id()));

            StockAlertHandler.checkStockAlerts(medicine);
            assertNull("The confirmed owner may clear their own stock warning",
                    DB.alerts().findById(alert.id()));
        } finally {
            if (oldPref == null) {
                PreferenceUtils.edit().remove(prefKey).commit();
            } else {
                PreferenceUtils.edit().putString(prefKey, oldPref).commit();
            }
            if (alert != null && alert.id() != null
                    && DB.alerts().findById(alert.id()) != null) {
                DB.alerts().deleteById(alert.id());
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().deleteById(medicine.getId());
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().deleteById(owner.getId());
            }
            if (stranger.getId() != null && DB.patients().findById(stranger.getId()) != null) {
                DB.patients().deleteById(stranger.getId());
            }
        }
    }
}
