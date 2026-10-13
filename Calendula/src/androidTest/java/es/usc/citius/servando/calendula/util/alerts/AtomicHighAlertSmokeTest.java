/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AtomicHighAlertSmokeTest {
    @Test
    public void failedSecondIntakeDeleteRollsBackAlertAndBothBlockedSchedules()
            throws Exception {
        assertTrue(DB.initialized);
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setName("Synthetic HIGH alert owner");
        owner.setCode("ci-high-alert-rollback-" + suffix);
        Medicine medicine = new Medicine("Synthetic alert medicine", Presentation.PILLS);
        Schedule first = null;
        Schedule second = null;
        EventInstance one = null;
        EventInstance two = null;
        AllergyPatientAlert failedAlert = new AllergyPatientAlert();
        AllergyPatientAlert validAlert = new AllergyPatientAlert();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_block_second_alert_event_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));

            RecurringEvent recur = new RecurringEvent.Builder()
                    .from(DateTime.now().minusDays(1))
                    .repeatEvery(8, Freq.HOURLY).build();
            first = new Schedule(recur);
            first.setPatient(owner);
            first.setMedicine(medicine);
            first.setDosage(1.0d);
            second = new Schedule(recur);
            second.setPatient(owner);
            second.setMedicine(medicine);
            second.setDosage(1.0d);
            assertEquals(1, DB.schedules().create(first));
            assertEquals(1, DB.schedules().create(second));
            one = new EventInstance(DateTime.now().plusMinutes(25),
                    EventType.MEDICATION_INTAKE);
            two = new EventInstance(DateTime.now().plusMinutes(30),
                    EventType.MEDICATION_INTAKE);
            one.setPatient(owner);
            two.setPatient(owner);
            one.setRef(first.getId());
            two.setRef(second.getId());
            one.addParam(EventInstance.PARAM_DOSE, 1.0d);
            two.addParam(EventInstance.PARAM_DOSE, 1.0d);
            assertEquals(1, DB.eventInstances().create(one));
            assertEquals(1, DB.eventInstances().create(two));
            failedAlert.setMedicine(medicine);
            failedAlert.setPatient(owner);
            failedAlert.setLevel(3);
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventInstances WHEN OLD._id = " + two.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic second event failure'); END;");
            boolean rejected = false;
            try {
                AlertManager.createAlert(failedAlert);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("HIGH alert must reject an incomplete schedule-blocking write", rejected);
            if (failedAlert.id() != null) {
                assertNull("Alert cannot commit if later schedule blocking failed",
                        DB.alerts().findById(failedAlert.id()));
            }
            Schedule storedFirst = DB.schedules().findById(first.getId());
            Schedule storedSecond = DB.schedules().findById(second.getId());
            assertNotNull(storedFirst);
            assertNotNull(storedSecond);
            assertFalse("First schedule BLOCKED state must roll back",
                    storedFirst.hasState(Schedule.ScheduleState.BLOCKED));
            assertFalse("Second schedule BLOCKED state must roll back",
                    storedSecond.hasState(Schedule.ScheduleState.BLOCKED));
            assertNotNull("First patient's pending dose must roll back",
                    DB.eventInstances().findById(one.getId()));
            assertNotNull("Second patient's pending dose must roll back",
                    DB.eventInstances().findById(two.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            validAlert.setMedicine(medicine);
            validAlert.setPatient(owner);
            validAlert.setLevel(3);
            AlertManager.createAlert(validAlert);
            assertNotNull(DB.alerts().findById(validAlert.id()));
            assertTrue(DB.schedules().findById(first.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            assertTrue(DB.schedules().findById(second.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            assertNull(DB.eventInstances().findById(one.getId()));
            assertNull(DB.eventInstances().findById(two.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            for (AllergyPatientAlert a : new AllergyPatientAlert[]{failedAlert, validAlert}) {
                if (a.id() != null && DB.alerts().findById(a.id()) != null) {
                    DB.alerts().remove(a);
                }
            }
            for (EventInstance e : new EventInstance[]{one, two}) {
                if (e != null && e.getId() != null
                        && DB.eventInstances().findById(e.getId()) != null) {
                    DB.eventInstances().remove(e);
                }
            }
            for (Schedule schedule : new Schedule[]{first, second}) {
                if (schedule != null && schedule.getId() != null
                        && DB.schedules().findById(schedule.getId()) != null) {
                    DB.schedules().remove(schedule);
                }
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }
}
