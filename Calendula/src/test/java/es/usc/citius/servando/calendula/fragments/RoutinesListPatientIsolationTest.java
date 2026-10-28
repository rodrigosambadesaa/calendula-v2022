package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;

import static org.junit.Assert.*;

/** Synthetic owner references only; does not access clinical SQLite rows. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RoutinesListPatientIsolationTest {

    @Test
    public void staleProfileAndMixedOwnerListMustNotAppearAfterAsyncQuery() {
        Patient a = patient(1001L);
        Patient b = patient(1002L);
        Routine aRoutine = routine(2001L, a);
        Routine bRoutine = routine(2002L, b);
        assertTrue(RoutinesListFragment.mayApplyRoutineReload(
                7L, 7L, a, patient(1001L), Collections.singletonList(aRoutine)));
        assertFalse(RoutinesListFragment.mayApplyRoutineReload(
                7L, 7L, a, b, Collections.singletonList(aRoutine)));
        assertFalse(RoutinesListFragment.mayApplyRoutineReload(
                7L, 8L, a, a, Collections.singletonList(aRoutine)));
        assertFalse(RoutinesListFragment.mayApplyRoutineReload(
                7L, 7L, a, a, Arrays.asList(aRoutine, bRoutine)));
        assertFalse(RoutinesListFragment.mayApplyRoutineReload(
                7L, 7L, a, a, Arrays.asList(aRoutine, null)));
        assertTrue(RoutinesListFragment.mayApplyRoutineReload(
                7L, 7L, a, a, Collections.emptyList()));
        assertFalse(RoutinesListFragment.mayApplyRoutineReload(
                7L, 7L, a, a, null));
        assertFalse(RoutinesListFragment.mayApplyRoutineReload(
                7L, 7L, null, a, Collections.emptyList()));
    }

    @Test
    public void staleRoutineCannotOpenEditorOrDeletionDialog() {
        Patient a = patient(1001L);
        Patient b = patient(1002L);
        Routine selected = routine(2001L, a);
        assertTrue(RoutinesListFragment.ownsRoutine(selected, a));
        assertFalse(RoutinesListFragment.ownsRoutine(selected, b));
        assertFalse(RoutinesListFragment.ownsRoutine(selected, null));
        assertFalse(RoutinesListFragment.ownsRoutine(null, a));
        assertFalse(RoutinesListFragment.ownsRoutine(routine(null, a), a));
        assertFalse(RoutinesListFragment.ownsRoutine(routine(2001L, null), a));
        assertFalse(RoutinesListFragment.ownsRoutine(routine(2001L, patient(0L)), a));
        Routine missingTime = routine(2001L, a);
        missingTime.setTime(null);
        assertFalse(RoutinesListFragment.ownsRoutine(missingTime, a));
        Routine missingName = routine(2001L, a);
        missingName.setName(null);
        assertFalse(RoutinesListFragment.ownsRoutine(missingName, a));
    }

    @Test
    public void reloadGenerationsArePerFragmentNotStatic() throws Exception {
        Field field = RoutinesListFragment.class.getDeclaredField("reloadGeneration");
        assertFalse(Modifier.isStatic(field.getModifiers()));
        field.setAccessible(true);
        AtomicLong a = (AtomicLong) field.get(new RoutinesListFragment());
        AtomicLong b = (AtomicLong) field.get(new RoutinesListFragment());
        assertNotSame(a, b);
        a.incrementAndGet();
        assertEquals(1L, a.get());
        assertEquals(0L, b.get());
    }

    private static Patient patient(Long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private static Routine routine(Long id, Patient patient) {
        Routine r = new Routine();
        r.setId(id);
        r.setPatient(patient);
        r.setName("Synthetic named routine");
        r.setTime(new org.joda.time.LocalTime(9, 15));
        return r;
    }
}
