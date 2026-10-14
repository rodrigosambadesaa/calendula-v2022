/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests a duplicate behind the current row, plus missing and corrupt IDs. */
public class MedicineDuplicateGuardTest {
    @Test
    public void ownRowFirstMustNotHideAnotherMatchingMedicine() {
        assertTrue(MedicineDuplicateGuard.hasConflictingMatch(
                201L, Arrays.asList(201L, 202L)));
        assertTrue(MedicineDuplicateGuard.hasConflictingMatch(
                201L, Arrays.asList(202L, 201L)));
        assertFalse(MedicineDuplicateGuard.hasConflictingMatch(
                201L, Collections.singletonList(201L)));
        assertFalse(MedicineDuplicateGuard.hasConflictingMatch(
                201L, Collections.emptyList()));
    }

    @Test
    public void everyExistingMatchConflictsWhenCreatingNewMedicine() {
        assertTrue(MedicineDuplicateGuard.hasConflictingMatch(
                null, Collections.singletonList(201L)));
        assertFalse(MedicineDuplicateGuard.hasConflictingMatch(
                null, Collections.emptyList()));
    }

    @Test
    public void failedQueriesAndBrokenRowsNeverAuthorizeSave() {
        assertTrue(MedicineDuplicateGuard.hasConflictingMatch(201L, null));
        assertTrue(MedicineDuplicateGuard.hasConflictingMatch(
                201L, Arrays.asList(201L, null)));
        assertTrue(MedicineDuplicateGuard.hasConflictingMatch(
                201L, Collections.singletonList(0L)));
        assertTrue(MedicineDuplicateGuard.hasConflictingMatch(
                201L, Collections.singletonList(-1L)));
    }
}
