/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure owner tests, without an Android context or patient database. */
public class AllergenDeletionGuardTest {
    @Test
    public void permitsOnlyPersistedAllergensOwnedByOpeningAndActivePatient() {
        assertTrue(AllergenDeletionGuard.isSamePersistedOwner(
                123L, 123L, 42L, 123L, 42L, 123L,
                "excipient", "excipient", "E42", "E42"));
        assertFalse(AllergenDeletionGuard.isSamePersistedOwner(
                123L, 124L, 42L, 123L, 42L, 123L,
                "excipient", "excipient", "E42", "E42"));
        assertFalse(AllergenDeletionGuard.isSamePersistedOwner(
                123L, 123L, 42L, 124L, 42L, 123L,
                "excipient", "excipient", "E42", "E42"));
        assertFalse(AllergenDeletionGuard.isSamePersistedOwner(
                123L, 123L, 42L, 123L, 42L, 124L,
                "excipient", "excipient", "E42", "E42"));
    }

    @Test
    public void rejectsReusedOrCorruptedAllergenIdentity() {
        assertFalse(AllergenDeletionGuard.isSamePersistedOwner(
                123L, 123L, 42L, 123L, 43L, 123L,
                "excipient", "excipient", "E42", "E42"));
        assertFalse(AllergenDeletionGuard.isSamePersistedOwner(
                123L, 123L, 42L, 123L, 42L, 123L,
                "excipient", "ingredient", "E42", "E42"));
        assertFalse(AllergenDeletionGuard.isSamePersistedOwner(
                123L, 123L, 42L, 123L, 42L, 123L,
                "excipient", "excipient", "E42", "E43"));
        assertFalse(AllergenDeletionGuard.isSamePersistedOwner(
                123L, 123L, 42L, 123L, 42L, 123L,
                null, "excipient", "E42", "E42"));
        assertFalse(AllergenDeletionGuard.isSamePersistedOwner(
                123L, 123L, null, 123L, 42L, 123L,
                "excipient", "excipient", "E42", "E42"));
        assertFalse(AllergenDeletionGuard.isSamePersistedOwner(
                0L, 0L, 42L, 0L, 42L, 0L,
                "excipient", "excipient", "E42", "E42"));
    }
}
