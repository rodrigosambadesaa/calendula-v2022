/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Uses only disposable synthetic SQLite records; never real patient data. */
@RunWith(AndroidJUnit4.class)
public class NotificationSnapshotSmokeTest {
    private static final double DOSE = 2.0;
    private static final float INITIAL_STOCK = 10.0f;

    private static final class Fixture {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final DateTime when = DateTime.now().plusHours(14).withMillisOfSecond(0);
        final Patient patient = new Patient();
        final Medicine[] medicines = {
                new Medicine("Synthetic snapshot medicine 1", Presentation.PILLS),
                new Medicine("Synthetic snapshot medicine 2", Presentation.PILLS)};
        final Schedule[] schedules = {
                new Schedule(medicines[0]), new Schedule(medicines[1])};
        final EventInstance[] intakes = {
                new EventInstance(when, EventType.MEDICATION_INTAKE),
                new EventInstance(when, EventType.MEDICATION_INTAKE)};
        final EventReminder reminder = new EventReminder(when, EventType.MEDICATION_INTAKE);

        Fixture(boolean secondInitiallyVisible) throws Exception {
            patient.setCode("ci-snapshot-patient-" + System.nanoTime());
            patient.setName("Synthetic notification snapshot patient");
            assertEquals(1, DB.patients().create(patient));
            createDose(0);
            if (secondInitiallyVisible) {
                createDose(1);
            }
            reminder.setPatient(patient);
            reminder.setNextTime(when);
            assertEquals(1, DB.eventReminders().create(reminder));
            assertNotNull(reminder.getId());
        }

        void createDose(int index) throws Exception {
            medicines[index].setPatient(patient);
            medicines[index].setStock(INITIAL_STOCK);
            assertEquals(1, DB.medicines().create(medicines[index]));
            schedules[index].setPatient(patient);
            assertEquals(1, DB.schedules().create(schedules[index]));
            intakes[index].setPatient(patient);
            intakes[index].setRef(schedules[index].getId());
            intakes[index].addParam(EventInstance.PARAM_DOSE, DOSE);
            assertEquals(1, DB.eventInstances().create(intakes[index]));
            assertNotNull(intakes[index].getId());
        }

        List<EventInstance> visible(int count) {
            return new ArrayList<>(Arrays.asList(intakes).subList(0, count));
        }

        void assertUnchanged(int count) {
            for (int i = 0; i < count; i++) {
                assertFalse(DB.eventInstances().findById(intakes[i].getId()).completed());
                assertEquals(INITIAL_STOCK,
                        DB.medicines().findById(medicines[i].getId()).getStock(), 0.001f);
            }
        }

        void cleanup() {
            if (reminder.getId() != null
                    && DB.eventReminders().findById(reminder.getId()) != null) {
                DB.eventReminders().remove(reminder);
            }
            for (EventInstance e : intakes) {
                if (e.getId() != null && DB.eventInstances().findById(e.getId()) != null) {
                    DB.eventInstances().remove(e);
                }
            }
            for (Schedule s : schedules) {
                if (s.getId() != null && DB.schedules().findById(s.getId()) != null) {
                    DB.schedules().remove(s);
                }
            }
            for (Medicine m : medicines) {
                if (m.getId() != null && DB.medicines().findById(m.getId()) != null) {
                    DB.medicines().remove(m);
                }
            }
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }

    @Test
    public void unchangedNotificationSnapshotCommitsExactlyDisplayedDosesOnce()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(true);
        try {
            String shown = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(shown);
            assertEquals(64, shown.length());
            assertEquals("Digest must not depend on DAO row ordering",
                    shown, IntakeSnapshot.fingerprint(Arrays.asList(x.intakes[1], x.intakes[0]), x.reminder));
            assertEquals(2, ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                    x.context, x.patient, x.when, x.reminder.getId(), shown));
            for (int i = 0; i < 2; i++) {
                assertTrue(DB.eventInstances().findById(x.intakes[i].getId()).completed());
                assertEquals(INITIAL_STOCK - DOSE,
                        DB.medicines().findById(x.medicines[i].getId()).getStock(), 0.001);
            }
            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), shown);
                fail("The same notification must not be able to confirm twice");
            } catch (RuntimeException expected) {
                // Consumed selection no longer matches the pending SQLite set.
            }
            for (int i = 0; i < 2; i++) {
                assertEquals(INITIAL_STOCK - DOSE,
                        DB.medicines().findById(x.medicines[i].getId()).getStock(), 0.001);
            }
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void newlyAddedDoseAfterPostingInvalidatesOriginalNotification()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(false);
        try {
            String shown = IntakeSnapshot.fingerprint(x.visible(1), x.reminder);
            x.createDose(1);
            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), shown);
                fail("Newly added medication was never displayed or consented");
            } catch (RuntimeException expected) {
                // No stock or intake modification is permitted.
            }
            x.assertUnchanged(2);
            assertEquals(2, ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                    x.context, x.patient, x.when, x.reminder.getId(),
                    IntakeSnapshot.fingerprint(x.visible(2), x.reminder)));
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void delayedReminderInvalidatesItsPreviousNotificationEvenIfDosesUnchanged()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(true);
        try {
            String shown = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(shown);
            x.reminder.setNextTime(x.when.plusMinutes(12));
            assertEquals(1, DB.eventReminders().update(x.reminder));
            assertEquals(x.when.plusMinutes(12),
                    DB.eventReminders().findById(x.reminder.getId()).getNextTime());

            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), shown);
                fail("A previous notification button cannot confirm a delayed reminder");
            } catch (RuntimeException expected) {
                // No dose changes, but the reminder revision changed.
            }
            x.assertUnchanged(2);
            assertNotNull(DB.eventReminders().findById(x.reminder.getId()));

            String refreshed = IntakeSnapshot.fingerprint(
                    x.visible(2), DB.eventReminders().findById(x.reminder.getId()));
            assertFalse("The delayed reminder must have a different action digest",
                    shown.equals(refreshed));
            assertEquals(2, ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                    x.context, x.patient, x.when, x.reminder.getId(), refreshed));
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void revisedDoseAfterPostingCannotBeConfirmedFromStaleAction()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(true);
        try {
            String shown = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            x.intakes[1].addParam(EventInstance.PARAM_DOSE, "3.0");
            assertEquals(1, DB.eventInstances().updateParams(
                    EventType.MEDICATION_INTAKE, x.when, x.schedules[1].getId(),
                    x.intakes[1].getParams()));
            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), shown);
                fail("A changed dose cannot be approved from an old notification");
            } catch (RuntimeException expected) {
                // Both doses and stock balances remain unchanged.
            }
            x.assertUnchanged(2);
            assertEquals(2, ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                    x.context, x.patient, x.when, x.reminder.getId(),
                    IntakeSnapshot.fingerprint(DB.eventInstances().findPending(
                            EventType.MEDICATION_INTAKE, x.when, x.patient), x.reminder)));
            assertEquals(8.0f,
                    DB.medicines().findById(x.medicines[0].getId()).getStock(), 0.001f);
            assertEquals(7.0f,
                    DB.medicines().findById(x.medicines[1].getId()).getStock(), 0.001f);
        } finally {
            x.cleanup();
        }
    }
}
