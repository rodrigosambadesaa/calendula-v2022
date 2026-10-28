package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.*;

/**
 * Genuine SQLite smoke: an old dialog must not erase another selected
 * patient's schedule or its pending/confirmed history. Synthetic rows only.
 */
@RunWith(AndroidJUnit4.class)
public class ActiveScheduleDeletionPatientSwitchSmokeTest {

    @Test
    public void staleDeleteDialogCannotRemoveAnotherPatientsSchedule() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previouslyActive = DB.patients().getActive(context);
        Patient a = patient("a");
        Patient b = patient("b");
        Medicine medicine = new Medicine("Synthetic schedule owner medicine", Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        EventInstance pending = new EventInstance(
                DateTime.now().plusHours(9), EventType.MEDICATION_INTAKE);
        EventInstance confirmed = new EventInstance(
                DateTime.now().minusHours(9), EventType.MEDICATION_INTAKE);
        try {
            assertEquals(1, DB.patients().create(a));
            assertEquals(1, DB.patients().create(b));
            medicine.setPatient(a);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(a);
            assertEquals(1, DB.schedules().create(schedule));
            pending.setPatient(a);
            pending.setRef(schedule.getId());
            pending.addParam(EventInstance.PARAM_DOSE, 1d);
            assertEquals(1, DB.eventInstances().create(pending));
            confirmed.setPatient(a);
            confirmed.setRef(schedule.getId());
            confirmed.addParam(EventInstance.PARAM_DOSE, 1d);
            confirmed.setCompleted(true);
            confirmed.setCompletedAt(DateTime.now().minusHours(8));
            assertEquals(1, DB.eventInstances().create(confirmed));

            DB.patients().setActive(a); // Dialog opened for patient A.
            DB.patients().setActive(b); // Profile switched before confirmation.
            boolean rejected = false;
            try {
                ScheduleUtils.instance().removeVerifiedActiveSchedule(
                        context, schedule, false);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Old dialog must reject a different active profile", rejected);
            assertNotNull("Schedule must survive rejected deletion",
                    DB.schedules().findById(schedule.getId()));
            assertNotNull("Pending intake remains intact",
                    DB.eventInstances().findById(pending.getId()));
            assertTrue("Previously completed history survives rejection",
                    DB.eventInstances().findById(confirmed.getId()).completed());

            // A fresh confirmation by the actual owner can still delete the
            // schedule, without erasing a previously completed intake.
            DB.patients().setActive(a);
            ScheduleUtils.instance().removeVerifiedActiveSchedule(
                    context, schedule, false);
            assertNull(DB.schedules().findById(schedule.getId()));
            assertNull(DB.eventInstances().findById(pending.getId()));
            assertTrue(DB.eventInstances().findById(confirmed.getId()).completed());
        } finally {
            for (EventInstance event : new EventInstance[]{pending, confirmed}) {
                if (event.getId() != null
                        && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (previouslyActive != null && previouslyActive.getId() != null
                    && DB.patients().findById(previouslyActive.getId()) != null) {
                DB.patients().setActive(previouslyActive);
            } else {
                PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).apply();
            }
            for (Patient p : new Patient[]{a, b}) {
                if (p.getId() != null && DB.patients().findById(p.getId()) != null) {
                    DB.patients().remove(p);
                }
            }
        }
    }

    private static Patient patient(String suffix) {
        Patient p = new Patient();
        p.setCode("ci-stale-schedule-delete-" + suffix + "-" + System.nanoTime());
        p.setName("Synthetic schedule deletion patient " + suffix);
        return p;
    }
}
