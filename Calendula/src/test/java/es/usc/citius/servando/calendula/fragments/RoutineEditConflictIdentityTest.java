/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Routine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/** Avoid blocking edits of the same persisted routine after ORM reload. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RoutineEditConflictIdentityTest {

    private Routine routine(long id) {
        Routine r = new Routine();
        r.setId(Long.valueOf(id));
        return r;
    }

    @Test
    public void sameSqliteRoutineDoesNotConflictAcrossDifferentLongInstances() {
        Routine loadedFromNameQuery = routine(888001L);
        Routine editedFromExistingScreen = routine(888001L);
        assertNotSame(loadedFromNameQuery.getId(), editedFromExistingScreen.getId());

        assertFalse(RoutineCreateOrEditFragment.conflictsWithEditedRoutine(
                loadedFromNameQuery, editedFromExistingScreen));
        assertFalse(RoutineCreateOrEditFragment.conflictsWithEditedRoutine(
                editedFromExistingScreen, editedFromExistingScreen));
    }

    @Test
    public void anotherRoutineStillConflictsWithExistingNameOrTime() {
        assertTrue(RoutineCreateOrEditFragment.conflictsWithEditedRoutine(
                routine(888002L), routine(888001L)));
        assertFalse(RoutineCreateOrEditFragment.conflictsWithEditedRoutine(
                null, routine(888001L)));
    }

    @Test
    public void unknownOrUnsavedIdentityCannotSilentlyBypassConflict() {
        assertTrue(RoutineCreateOrEditFragment.conflictsWithEditedRoutine(
                new Routine(), routine(888001L)));
        assertTrue(RoutineCreateOrEditFragment.conflictsWithEditedRoutine(
                routine(888002L), new Routine()));
    }
}
