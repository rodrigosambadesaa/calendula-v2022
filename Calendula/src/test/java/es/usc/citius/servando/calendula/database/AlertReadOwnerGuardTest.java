/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure ID tests: Android's Patient initializer calls Color.parseColor and
 * cannot be constructed in local JVM tests (Android instrumentation covers it).
 */
public class AlertReadOwnerGuardTest {
    @Test
    public void onlyExactMedicineAndPatientIdsAreAllowed() {
        assertTrue(PatientAlertDao.matchingOwnerIds(11002L, 11001L, 11002L, 11001L));
        assertFalse(PatientAlertDao.matchingOwnerIds(11002L, 11001L, 11003L, 11001L));
        assertFalse(PatientAlertDao.matchingOwnerIds(11002L, 11001L, 11002L, 11003L));
        assertFalse(PatientAlertDao.matchingOwnerIds(11002L, 11001L, null, 11001L));
        assertFalse(PatientAlertDao.matchingOwnerIds(11002L, 11001L, 11002L, null));
    }

    @Test
    public void missingOrInvalidPatientAndMedicineIdsAreRejected() {
        assertFalse(PatientAlertDao.matchingOwnerIds(null, 11001L, 11002L, 11001L));
        assertFalse(PatientAlertDao.matchingOwnerIds(11002L, null, 11002L, 11001L));
        assertFalse(PatientAlertDao.matchingOwnerIds(0L, 11001L, 0L, 11001L));
        assertFalse(PatientAlertDao.matchingOwnerIds(11002L, 0L, 11002L, 0L));
        assertFalse(PatientAlertDao.matchingOwnerIds(-1L, 11001L, -1L, 11001L));
    }
}
