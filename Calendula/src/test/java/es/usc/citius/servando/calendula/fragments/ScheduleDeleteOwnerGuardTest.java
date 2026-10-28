package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;

import static org.junit.Assert.*;

/** Clinical ownership only; no patient records are accessed or modified. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScheduleDeleteOwnerGuardTest {
    @Test
    public void staleOrUnassignedScheduleCannotOpenDestructiveDialog() {
        Patient a = patient(4100L);
        Patient b = patient(4101L);
        Schedule owned = new Schedule();
        owned.setId(902L);
        owned.setPatient(a);
        assertTrue(ScheduleListFragment.canReviewDeleteForActivePatient(owned, a));
        assertTrue(ScheduleListFragment.canReviewDeleteForActivePatient(
                owned, patient(4100L)));
        assertFalse(ScheduleListFragment.canReviewDeleteForActivePatient(owned, b));
        assertFalse(ScheduleListFragment.canReviewDeleteForActivePatient(owned, null));
        assertFalse(ScheduleListFragment.canReviewDeleteForActivePatient(null, a));

        owned.setId(null);
        assertFalse(ScheduleListFragment.canReviewDeleteForActivePatient(owned, a));
        owned.setId(902L);
        owned.setPatient(null);
        assertFalse(ScheduleListFragment.canReviewDeleteForActivePatient(owned, a));
        owned.setPatient(patient(0L));
        assertFalse(ScheduleListFragment.canReviewDeleteForActivePatient(owned, a));
    }

    private static Patient patient(Long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }
}
