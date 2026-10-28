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
import es.usc.citius.servando.calendula.persistence.Schedule;

import static org.junit.Assert.*;

/** Synthetic schedule identities only, no patient records or SQLite writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScheduleListPatientReloadTest {
    @Test
    public void lateCallbackCannotRenderPreviousPatientsSchedules() {
        Patient a = patient(501L);
        Patient b = patient(502L);
        Schedule own = schedule(801L, a);
        Schedule foreign = schedule(802L, b);
        assertTrue(ScheduleListFragment.mayApplyScheduleReload(
                4L, 4L, a, patient(501L), Collections.singletonList(own)));
        assertFalse("The profile switched while the query was running",
                ScheduleListFragment.mayApplyScheduleReload(
                        4L, 4L, a, b, Collections.singletonList(own)));
        assertFalse("Another query has already superseded this result",
                ScheduleListFragment.mayApplyScheduleReload(
                        4L, 5L, a, a, Collections.singletonList(own)));
        assertFalse("Reject all rows if any one has a foreign owner",
                ScheduleListFragment.mayApplyScheduleReload(
                        4L, 4L, a, a, Arrays.asList(own, foreign)));
        assertFalse(ScheduleListFragment.mayApplyScheduleReload(
                4L, 4L, a, a, Arrays.asList(own, null)));
        assertFalse(ScheduleListFragment.mayApplyScheduleReload(
                4L, 4L, a, a, null));
        assertFalse(ScheduleListFragment.mayApplyScheduleReload(
                4L, 4L, null, a, Collections.emptyList()));
        assertTrue(ScheduleListFragment.mayApplyScheduleReload(
                4L, 4L, a, a, Collections.emptyList()));
    }

    @Test
    public void everyFragmentHasIndependentReloadGeneration() throws Exception {
        Field field = ScheduleListFragment.class.getDeclaredField("reloadGeneration");
        assertFalse(Modifier.isStatic(field.getModifiers()));
        ScheduleListFragment a = new ScheduleListFragment();
        ScheduleListFragment b = new ScheduleListFragment();
        field.setAccessible(true);
        AtomicLong aGeneration = (AtomicLong) field.get(a);
        AtomicLong bGeneration = (AtomicLong) field.get(b);
        assertNotSame(aGeneration, bGeneration);
        aGeneration.incrementAndGet();
        assertEquals(1L, aGeneration.get());
        assertEquals(0L, bGeneration.get());
    }

    @Test
    public void unsavedOrOrphanSchedulesDoNotAuthorizeClinicalRendering() {
        Patient owner = patient(501L);
        assertFalse(ScheduleListFragment.ownsEverySchedule(owner,
                Collections.singletonList(schedule(null, owner))));
        assertFalse(ScheduleListFragment.ownsEverySchedule(owner,
                Collections.singletonList(schedule(801L, null))));
        assertFalse(ScheduleListFragment.ownsEverySchedule(patient(0L),
                Collections.singletonList(schedule(801L, patient(0L)))));
    }

    private static Patient patient(Long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private static Schedule schedule(Long id, Patient patient) {
        Schedule s = new Schedule();
        s.setId(id);
        s.setPatient(patient);
        return s;
    }
}
