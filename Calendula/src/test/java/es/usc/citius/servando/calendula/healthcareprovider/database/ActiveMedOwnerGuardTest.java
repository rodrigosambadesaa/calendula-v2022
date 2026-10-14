/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.database;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** No Android runtime: verify owner identity and provider linkage by IDs. */
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
    public void localMedicineCanOnlyJoinItsExactProviderEntry() {
        assertTrue(ActiveMedOwnerGuard.sameProviderLink(511L, 511L));
        assertFalse(ActiveMedOwnerGuard.sameProviderLink(511L, 512L));
        assertFalse(ActiveMedOwnerGuard.sameProviderLink(null, 511L));
        assertFalse(ActiveMedOwnerGuard.sameProviderLink(511L, null));
        assertFalse(ActiveMedOwnerGuard.sameProviderLink(0L, 0L));
    }
}
