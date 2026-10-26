package es.usc.citius.servando.calendula.activities;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.*;

/** Synthetic owner identifiers only; no medication or provider data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CalendarActivityPatientIsolationTest {
    @Test
    public void pickupCalendarModelMustNotBeGlobalAcrossPatients() throws Exception {
        Field storage = CalendarActivity.class.getDeclaredField("dispensationInfoStore");
        assertFalse("A pickup calendar belongs to one activity, not all users",
                Modifier.isStatic(storage.getModifiers()));
    }

    @Test
    public void onlyOriginalPersistedPatientMayReceiveBackgroundPickupResults() {
        Patient requested = patient(1048577L);
        Patient samePersistedRow = patient(Long.valueOf("1048577"));
        Patient another = patient(1048578L);
        assertTrue(CalendarActivity.samePersistedPatient(requested, samePersistedRow));
        assertFalse(CalendarActivity.samePersistedPatient(requested, another));
        assertFalse(CalendarActivity.samePersistedPatient(requested, null));
        assertFalse(CalendarActivity.samePersistedPatient(null, requested));
        assertFalse(CalendarActivity.samePersistedPatient(patient(0L), patient(0L)));
        assertFalse(CalendarActivity.samePersistedPatient(new Patient(), new Patient()));
    }

    private static Patient patient(Long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }
}
