package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OfficialReminderLinkSelectorTest {

    private static Patient patient(long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private static Schedule link(long id, Patient patient, Patient medicineOwner) {
        Medicine medicine = new Medicine("Synthetic local medicine");
        medicine.setPatient(medicineOwner);
        medicine.setId(id + 1000L);
        Schedule schedule = new Schedule(medicine);
        schedule.setId(id);
        schedule.setPatient(patient);
        return schedule;
    }

    @Test
    public void zeroOneOrMultipleValidSchedulesAreHandledExplicitly() {
        Schedule first = link(10L, patient(1L), patient(1L));
        Schedule second = link(20L, patient(1L), patient(1L));
        assertEquals(OfficialReminderLinkSelector.Action.CREATE,
                OfficialReminderLinkSelector.inspect(1L, 1L, 1L,
                        Collections.emptyList()).action);
        OfficialReminderLinkSelector.Selection one =
                OfficialReminderLinkSelector.inspect(1L, 1L, 1L,
                        Collections.singletonList(first));
        assertEquals(OfficialReminderLinkSelector.Action.EDIT, one.action);
        assertEquals(10L, one.schedules.get(0).getId().longValue());
        OfficialReminderLinkSelector.Selection many =
                OfficialReminderLinkSelector.inspect(1L, 1L, 1L,
                        Arrays.asList(first, second));
        assertEquals(OfficialReminderLinkSelector.Action.CHOOSE, many.action);
        assertEquals(2, many.schedules.size());
    }

    @Test
    public void anyForeignOrOwnerlessLinkRejectsWholeSelection() {
        Schedule owned = link(10L, patient(1L), patient(1L));
        Schedule foreignSchedule = link(11L, patient(2L), patient(2L));
        Schedule foreignMedicine = link(12L, patient(1L), patient(2L));
        Schedule ownerlessMedicine = link(13L, patient(1L), new Patient());
        Schedule missingMedicine = link(14L, patient(1L), patient(1L));
        missingMedicine.setMedicine(null);
        Schedule unsavedMedicine = link(15L, patient(1L), patient(1L));
        unsavedMedicine.getMedicine().setId(null);
        for (Schedule invalid : Arrays.asList(
                foreignSchedule, foreignMedicine, ownerlessMedicine, missingMedicine,
                unsavedMedicine)) {
            OfficialReminderLinkSelector.Selection result =
                    OfficialReminderLinkSelector.inspect(1L, 1L, 1L,
                            Arrays.asList(owned, invalid));
            assertEquals(OfficialReminderLinkSelector.Action.UNAVAILABLE, result.action);
            assertTrue(result.schedules.isEmpty());
        }
        assertEquals(OfficialReminderLinkSelector.Action.UNAVAILABLE,
                OfficialReminderLinkSelector.inspect(1L, 2L, 1L,
                        Collections.singletonList(owned)).action);
        assertEquals(OfficialReminderLinkSelector.Action.UNAVAILABLE,
                OfficialReminderLinkSelector.inspect(1L, 1L, 2L,
                        Collections.singletonList(owned)).action);
        assertEquals(OfficialReminderLinkSelector.Action.UNAVAILABLE,
                OfficialReminderLinkSelector.inspect(null, 1L, 1L,
                        Collections.singletonList(owned)).action);
        assertEquals(OfficialReminderLinkSelector.Action.UNAVAILABLE,
                OfficialReminderLinkSelector.inspect(1L, 1L, 1L, null).action);
    }
}
