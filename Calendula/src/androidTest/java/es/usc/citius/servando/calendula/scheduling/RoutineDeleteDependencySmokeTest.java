/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.HashMap;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RoutineDeleteDependencySmokeTest {
    @Test
    public void secondScheduleFailureRollsBackFirstScheduleAndItsPendingDose() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        Patient owner = new Patient();
        owner.setCode("ci-multischedule-atomic-owner-" + System.nanoTime());
        owner.setName("Synthetic atomic routine deletion owner");
        Medicine medicine = new Medicine("Synthetic atomic routine medication", Presentation.PILLS);
        Routine routine = null;
        Schedule first = null;
        Schedule second = null;
        es.usc.citius.servando.calendula.scheduling.model.EventInstance event = null;
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_routine_second_schedule_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            DB.patients().setActive(owner);
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            routine = new Routine(owner, new LocalTime(14, 35), "Synthetic shared routine");
            routine.setDailyEventType(RepeatType.TIME_OF_DAY);
            assertEquals(1, DB.routines().create(routine));

            DailyFixedTime ref = new DailyFixedTime(
                    routine.getId(), DailyFixedTime.ReferenceType.ROUTINE);
            RecurringEvent recurrence = new RecurringEvent.Builder()
                    .from(DateTime.now().minusDays(1))
                    .repeatEvery(1, Freq.DAILY)
                    .atFixedTime(ref)
                    .build();
            java.util.Map<Long, Double> dose = new HashMap<>();
            dose.put(routine.getId(), 1d);
            first = new Schedule(recurrence);
            first.setPatient(owner);
            first.setMedicine(medicine);
            first.setDosages(dose);
            second = new Schedule(recurrence);
            second.setPatient(owner);
            second.setMedicine(medicine);
            second.setDosages(new HashMap<>(dose));
            assertEquals(1, DB.schedules().create(first));
            assertEquals(1, DB.schedules().create(second));
            assertTrue("The second schedule must have the larger ID",
                    second.getId() > first.getId());

            event = new es.usc.citius.servando.calendula.scheduling.model.EventInstance(
                    DateTime.now().plusMinutes(20),
                    es.usc.citius.servando.calendula.scheduling.model.EventType.MEDICATION_INTAKE);
            event.setPatient(owner);
            event.setRef(first.getId());
            event.addParam(
                    es.usc.citius.servando.calendula.scheduling.model.EventInstance.PARAM_DOSE,
                    1.0d);
            assertEquals(1, DB.eventInstances().create(event));

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON NewSchedules WHEN OLD._id = " + second.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic second schedule abort'); END;");
            boolean rejected = false;
            try {
                ScheduleUtils.instance().deleteRoutineWithLinkedSchedules(context, routine);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Second schedule failure must reject complete deletion", rejected);
            assertNotNull("First schedule must roll back", DB.schedules().findById(first.getId()));
            assertNotNull("Second schedule must survive", DB.schedules().findById(second.getId()));
            assertNotNull("First schedule's pending dose must roll back",
                    DB.eventInstances().findById(event.getId()));
            assertNotNull("Shared routine must survive unchanged",
                    DB.routines().findById(routine.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            ScheduleUtils.instance().deleteRoutineWithLinkedSchedules(context, routine);
            assertNull(DB.schedules().findById(first.getId()));
            assertNull(DB.schedules().findById(second.getId()));
            assertNull(DB.routines().findById(routine.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (event != null && event.getId() != null
                    && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
            for (Schedule schedule : new Schedule[]{first, second}) {
                if (schedule != null && schedule.getId() != null
                        && DB.schedules().findById(schedule.getId()) != null) {
                    DB.schedules().remove(schedule);
                }
            }
            if (routine != null && routine.getId() != null
                    && DB.routines().findById(routine.getId()) != null) {
                DB.routines().remove(routine);
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (previous != null && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).apply();
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }

    @Test
    public void failedLinkedScheduleRemovalCannotLeaveDanglingRoutineReference() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        Patient owner = new Patient();
        owner.setCode("ci-routine-delete-owner-" + System.nanoTime());
        owner.setName("Synthetic routine deletion owner");
        Medicine medicine = new Medicine("Synthetic routine deletion medicine", Presentation.PILLS);
        Routine routine = null;
        Routine mandatory = null;
        Schedule schedule = null;
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_routine_delete_abort_schedule";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            DB.patients().setActive(owner);
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            routine = new Routine(owner, new LocalTime(16, 45), "Synthetic routine");
            routine.setDailyEventType(RepeatType.TIME_OF_DAY);
            assertEquals(1, DB.routines().create(routine));
            DailyFixedTime time = new DailyFixedTime(
                    routine.getId(), DailyFixedTime.ReferenceType.ROUTINE);
            RecurringEvent recur = new RecurringEvent.Builder()
                    .from(DateTime.now().minusDays(1))
                    .repeatEvery(1, Freq.DAILY)
                    .atFixedTime(time)
                    .build();
            schedule = new Schedule(recur);
            schedule.setPatient(owner);
            schedule.setMedicine(medicine);
            HashMap<Long, Double> doses = new HashMap<>();
            doses.put(routine.getId(), 1d);
            schedule.setDosages(doses);
            assertEquals(1, DB.schedules().create(schedule));

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON NewSchedules WHEN OLD._id = " + schedule.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic schedule removal failure'); END;");
            boolean rejected = false;
            try {
                ScheduleUtils.instance().deleteRoutineWithLinkedSchedules(context, routine);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("A linked schedule SQL failure must abort routine deletion", rejected);
            assertNotNull("The original routine must remain a valid time source",
                    DB.routines().findById(routine.getId()));
            assertNotNull("The linked prescription must remain unchanged",
                    DB.schedules().findById(schedule.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            ScheduleUtils.instance().deleteRoutineWithLinkedSchedules(context, routine);
            assertNull("Linked schedule must be removed before its routine",
                    DB.schedules().findById(schedule.getId()));
            assertNull("Routine can be deleted only after dependent schedule removal",
                    DB.routines().findById(routine.getId()));
            mandatory = new Routine(owner, new LocalTime(13, 0),
                    "Synthetic mandatory meal-time routine");
            mandatory.setDailyEventType(RepeatType.CD);
            assertEquals(1, DB.routines().create(mandatory));
            boolean mandatoryRejected = false;
            try {
                ScheduleUtils.instance().deleteRoutineWithLinkedSchedules(context, mandatory);
            } catch (IllegalArgumentException expected) {
                mandatoryRejected = true;
            }
            assertTrue("Mandatory meal routines may never be erased by routine editor",
                    mandatoryRejected);
            assertNotNull(DB.routines().findById(mandatory.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (schedule != null && schedule.getId() != null
                    && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (routine != null && routine.getId() != null
                    && DB.routines().findById(routine.getId()) != null) {
                DB.routines().remove(routine);
            }
            if (mandatory != null && mandatory.getId() != null
                    && DB.routines().findById(mandatory.getId()) != null) {
                DB.routines().remove(mandatory);
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (previous != null && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).apply();
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }
}
