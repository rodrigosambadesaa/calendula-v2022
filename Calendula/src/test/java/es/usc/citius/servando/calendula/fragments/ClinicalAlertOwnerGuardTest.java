/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure identity checks, without access to Android or patients' SQLite data. */
public class ClinicalAlertOwnerGuardTest {
    @Test
    public void selectedMedicineMayOnlyShowCurrentPatientAlerts() {
        assertTrue(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, 19L, 20L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, 20L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 9L, 19L, 19L));
    }

    @Test
    public void missingOrUnsavedRecordsNeverAuthorizeClinicalAlertDisplay() {
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(null, 19L, 8L, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, null, 8L, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, null, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, null, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, 19L, null));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(0L, 19L, 0L, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, -1L, 8L, -1L, -1L));
    }
}
