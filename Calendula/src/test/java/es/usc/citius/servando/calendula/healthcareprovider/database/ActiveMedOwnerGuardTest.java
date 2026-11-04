/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.database;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** No Android runtime: verify owner identity and provider linkage by IDs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ActiveMedOwnerGuardTest {
    @Test
    public void providerRecordMustBelongToCurrentPatient() {
        assertTrue(ActiveMedOwnerGuard.samePatient(91L, 91L));
        assertFalse(ActiveMedOwnerGuard.samePatient(91L, 92L));
        assertFalse(ActiveMedOwnerGuard.samePatient(null, 91L));
        assertFalse(ActiveMedOwnerGuard.samePatient(91L, null));
        assertFalse(ActiveMedOwnerGuard.samePatient(0L, 0L));
        assertFalse(ActiveMedOwnerGuard.samePatient(-1L, -1L));
    }

    @Test
    public void stalePatientObjectWithRecycledDatabaseIdIsNotAuthorized() {
        es.usc.citius.servando.calendula.persistence.Patient saved =
                new es.usc.citius.servando.calendula.persistence.Patient();
        saved.setId(4242L);
        saved.setCode("synthetic-current-profile");
        es.usc.citius.servando.calendula.persistence.Patient stale =
                new es.usc.citius.servando.calendula.persistence.Patient();
        stale.setId(4242L);
        stale.setCode("synthetic-old-profile");

        assertFalse(ActiveMedOwnerGuard.samePersistedPatient(stale, saved));
        assertTrue(ActiveMedOwnerGuard.samePersistedPatient(saved, saved));
        stale.setCode("synthetic-current-profile");
        assertTrue(ActiveMedOwnerGuard.samePersistedPatient(stale, saved));
        stale.setCode(null);
        assertFalse(ActiveMedOwnerGuard.samePersistedPatient(stale, saved));
        stale.setCode(" ");
        assertFalse(ActiveMedOwnerGuard.samePersistedPatient(stale, saved));
        stale.setCode("synthetic-current-profile");
        stale.setId(0L);
        assertFalse(ActiveMedOwnerGuard.samePersistedPatient(stale, saved));
        assertFalse(ActiveMedOwnerGuard.samePersistedPatient(null, saved));
    }

    @Test
    public void localMedicineCanOnlyJoinItsExactProviderEntry() {
        assertTrue(ActiveMedOwnerGuard.sameProviderLink(511L, 511L));
        assertFalse(ActiveMedOwnerGuard.sameProviderLink(511L, 512L));
        assertFalse(ActiveMedOwnerGuard.sameProviderLink(null, 511L));
        assertFalse(ActiveMedOwnerGuard.sameProviderLink(511L, null));
        assertFalse(ActiveMedOwnerGuard.sameProviderLink(0L, 0L));
    }
}
