/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/** A search result must never be applied to a different clinical profile. */
public class AllergenSearchOwnerGuardTest {
    private static final String FIRST = "synthetic-allergy-patient-a";
    private static final String SECOND = "synthetic-allergy-patient-b";

    private static String firstFingerprint() {
        return AllergenSearchOwnerGuard.fingerprint(FIRST);
    }

    @Test
    public void onlyTheExactOriginalPatientMayReceiveSearchResults() {
        String owner = firstFingerprint();
        assertTrue(AllergenSearchOwnerGuard.samePatient(11L, owner, 11L, FIRST));
        assertFalse(AllergenSearchOwnerGuard.samePatient(11L, owner, 12L, FIRST));
        assertFalse("Reused same SQLite ID cannot reveal the replacement patient's allergies",
                AllergenSearchOwnerGuard.samePatient(11L, owner, 11L, SECOND));
        assertFalse(AllergenSearchOwnerGuard.samePatient(null, owner, 11L, FIRST));
        assertFalse(AllergenSearchOwnerGuard.samePatient(11L, null, 11L, FIRST));
        assertFalse(AllergenSearchOwnerGuard.samePatient(11L, owner, 11L, null));
        assertFalse(AllergenSearchOwnerGuard.samePatient(0L, owner, 0L, FIRST));
        assertFalse(AllergenSearchOwnerGuard.samePatient(-1L, owner, -1L, FIRST));
    }

    @Test
    public void insertedAllergenMustBelongToTheOpeningAndActiveStablePatient() {
        String owner = firstFingerprint();
        assertTrue(AllergenSearchOwnerGuard.canStore(
                11L, owner, 11L, FIRST, 11L, FIRST));
        assertFalse(AllergenSearchOwnerGuard.canStore(
                11L, owner, 12L, FIRST, 11L, FIRST));
        assertFalse(AllergenSearchOwnerGuard.canStore(
                11L, owner, 11L, SECOND, 11L, FIRST));
        assertFalse(AllergenSearchOwnerGuard.canStore(
                11L, owner, 11L, FIRST, 12L, FIRST));
        assertFalse(AllergenSearchOwnerGuard.canStore(
                11L, owner, 11L, FIRST, 11L, SECOND));
        assertFalse(AllergenSearchOwnerGuard.canStore(
                11L, owner, 11L, FIRST, 11L, null));
        assertFalse(AllergenSearchOwnerGuard.canStore(
                0L, owner, 0L, FIRST, 0L, FIRST));
    }

    @Test
    public void restorationFingerprintNeverContainsRawPatientIdentifier() {
        String hash = firstFingerprint();
        assertTrue(hash != null && hash.length() == 64);
        assertFalse(hash.contains(FIRST));
        assertFalse(hash.equals(AllergenSearchOwnerGuard.fingerprint(SECOND)));
        assertTrue(hash.equals(AllergenSearchOwnerGuard.fingerprint(FIRST)));
        assertTrue(AllergenSearchOwnerGuard.fingerprint(null) == null);
        assertTrue(AllergenSearchOwnerGuard.fingerprint("  ") == null);
    }
}
