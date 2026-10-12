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
import static org.junit.Assert.assertNull;
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
    public void wrongTypedDoseBundleCannotProduceAnActionableNotificationFingerprint()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(false);
        try {
            List<EventInstance> displayed = x.visible(1);
            String original = IntakeSnapshot.fingerprint(displayed, x.reminder);
            assertNotNull(original);
            // Some Android Bundle.getString implementations return null,
            // not an exception, for an unexpected Long. That must never
            // masquerade as an intentionally absent official dose.
            x.intakes[0].getParams().putLong(EventInstance.PARAM_DOSE, 7L);
            assertNull("Wrong-typed dose cannot produce an event fingerprint",
                    IntakeSnapshot.fingerprint(displayed));
            assertNull("Wrong-typed dose cannot authorize a notification action",
                    IntakeSnapshot.fingerprint(displayed, x.reminder));
            x.intakes[0].getParams().putString(EventInstance.PARAM_DOSE, "2.0");
            assertEquals("Restoring the original dose restores stable consent",
                    original, IntakeSnapshot.fingerprint(displayed, x.reminder));
            x.assertUnchanged(1);
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
            // Persist an independently updated reminder revision in real
            // SQLite. Legacy ORMLite whole-object update can return 0 for
            // this fixture; copy the provider's serialized DateTime value
            // from a donor row rather than guessing the column's format.
            EventReminder donor = new EventReminder(x.when, EventType.MEDICATION_INTAKE);
            donor.setPatient(x.patient);
            donor.setNextTime(x.when.plusMinutes(12));
            try {
                DB.eventReminders().save(donor);
                assertNotNull(donor.getId());
                DB.helper().getWritableDatabase().execSQL(
                        "UPDATE EventReminders SET NextTime = "
                                + "(SELECT NextTime FROM EventReminders WHERE _id = ?) "
                                + "WHERE _id = ?",
                        new Object[]{donor.getId(), x.reminder.getId()});
            } finally {
                if (donor.getId() != null
                        && DB.eventReminders().findById(donor.getId()) != null) {
                    DB.eventReminders().remove(donor);
                }
            }
            assertEquals("The independent delay must really reach SQLite",
                    x.when.plusMinutes(12),
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
    public void cancelledDoseAfterPostingInvalidatesEntireNotification()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(true);
        try {
            String shown = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(shown);
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE EventInstances SET Cancelled = 1 WHERE _id = ?",
                    new Object[]{x.intakes[1].getId()});
            assertTrue(DB.eventInstances().findById(x.intakes[1].getId()).cancelled());

            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), shown);
                fail("A cancelled dose must invalidate the original two-dose action");
            } catch (RuntimeException expected) {
                // Never confirm the remaining dose by an out-of-date button.
            }
            x.assertUnchanged(2);
            assertNotNull(DB.eventReminders().findById(x.reminder.getId()));
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void reassignedDoseAfterPostingCannotConfirmOriginalPatientsMedication()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(true);
        Patient other = new Patient();
        other.setCode("ci-snapshot-other-" + System.nanoTime());
        other.setName("Synthetic unrelated notification patient");
        try {
            assertEquals(1, DB.patients().create(other));
            String shown = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(shown);
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE EventInstances SET Patient = ? WHERE _id = ?",
                    new Object[]{other.getId(), x.intakes[1].getId()});
            assertEquals(other.getId(), DB.eventInstances()
                    .findById(x.intakes[1].getId()).getPatient().getId());

            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), shown);
                fail("An old notification may never approve an event moved to another patient");
            } catch (RuntimeException expected) {
                // Both stocks and event completion flags must be untouched.
            }
            x.assertUnchanged(2);
            assertNotNull(DB.eventReminders().findById(x.reminder.getId()));
        } finally {
            x.cleanup();
            if (other.getId() != null && DB.patients().findById(other.getId()) != null) {
                DB.patients().remove(other);
            }
        }
    }

    @Test
    public void replacedMedicineOnUnchangedScheduleCannotBeConfirmedFromStaleAction()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(true);
        Medicine replacement = new Medicine("Synthetic replacement medicine", Presentation.PILLS);
        try {
            String shown = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(shown);

            replacement.setPatient(x.patient);
            replacement.setStock(INITIAL_STOCK);
            assertEquals(1, DB.medicines().create(replacement));
            assertNotNull(replacement.getId());
            // Leave the displayed event ID, dose, schedule ID and patient
            // untouched. Only its schedule's medicine pointer changes.
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE NewSchedules SET Medicine = ? WHERE _id = ?",
                    new Object[]{replacement.getId(), x.schedules[1].getId()});
            assertEquals(replacement.getId(), DB.schedules()
                    .findById(x.schedules[1].getId()).getMedicine().getId());
            assertFalse("Medicine substitution must invalidate the notification",
                    shown.equals(IntakeSnapshot.fingerprint(x.visible(2), x.reminder)));

            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), shown);
                fail("An old button may not acknowledge a substituted medicine");
            } catch (RuntimeException expected) {
                // Entire SQLite batch must roll back on stale consent.
            }
            x.assertUnchanged(2);
            assertEquals(INITIAL_STOCK, DB.medicines()
                    .findById(replacement.getId()).getStock(), 0.001f);
            assertNotNull(DB.eventReminders().findById(x.reminder.getId()));
        } finally {
            x.cleanup();
            if (replacement.getId() != null
                    && DB.medicines().findById(replacement.getId()) != null) {
                DB.medicines().remove(replacement);
            }
        }
    }

    @Test
    public void staleCancelButtonCannotCancelNewlyAddedMedication()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(false);
        try {
            String shown = IntakeSnapshot.fingerprint(x.visible(1), x.reminder);
            assertNotNull(shown);
            x.createDose(1);

            assertFalse("A notification must not cancel a dose never displayed",
                    Agenda.instance().cancelReminderWithSnapshot(
                            x.context, x.reminder.getId(), shown));
            x.assertUnchanged(2);
            assertFalse(DB.eventInstances().findById(x.intakes[0].getId()).cancelled());
            assertFalse(DB.eventInstances().findById(x.intakes[1].getId()).cancelled());
            assertNotNull(DB.eventReminders().findById(x.reminder.getId()));

            String refreshed = IntakeSnapshot.fingerprint(
                    DB.eventInstances().findPending(
                            EventType.MEDICATION_INTAKE, x.when, x.patient),
                    x.reminder);
            assertTrue("A fresh user decision may cancel the current group",
                    Agenda.instance().cancelReminderWithSnapshot(
                            x.context, x.reminder.getId(), refreshed));
            for (int i = 0; i < 2; i++) {
                assertTrue(DB.eventInstances().findById(x.intakes[i].getId()).cancelled());
                assertEquals(INITIAL_STOCK,
                        DB.medicines().findById(x.medicines[i].getId()).getStock(), 0.001f);
            }
            assertNull(DB.eventReminders().findById(x.reminder.getId()));
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void staleDelayButtonCannotPostponeRevisedMedication()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(true);
        try {
            String shown = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(shown);
            x.intakes[1].addParam(EventInstance.PARAM_DOSE, "3.0");
            assertEquals(1, DB.eventInstances().updateParams(
                    EventType.MEDICATION_INTAKE, x.when, x.schedules[1].getId(),
                    x.intakes[1].getParams()));

            assertFalse("Stale snooze must not reschedule a changed dose",
                    Agenda.instance().delayReminderWithSnapshot(
                            x.context, x.reminder.getId(), shown, 180));
            assertEquals("Rejected snooze must leave the persisted alarm time unchanged",
                    x.when, DB.eventReminders().findById(x.reminder.getId()).getNextTime());
            x.assertUnchanged(2);
            assertNotNull(DB.eventReminders().findById(x.reminder.getId()));

            assertFalse("A legacy action without user-visible consent is not actionable",
                    Agenda.instance().delayReminderWithSnapshot(
                            x.context, x.reminder.getId(), null, 180));
            assertEquals(x.when,
                    DB.eventReminders().findById(x.reminder.getId()).getNextTime());
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void renamedMedicineAfterDisplayCannotBeConfirmedFromOldNotification()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(true);
        try {
            String shown = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(shown);
            // The original notification displayed this old medicine name.
            // Neither event identity, dose nor its schedule ID is changed.
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE Medicines SET Name = ? WHERE _id = ?",
                    new Object[]{"Synthetic renamed medicine", x.medicines[1].getId()});
            assertEquals("Synthetic renamed medicine",
                    DB.medicines().findById(x.medicines[1].getId()).getName());

            String current = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(current);
            assertFalse("Changed rendered medicine identity invalidates old consent",
                    shown.equals(current));
            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), shown);
                fail("Stale notification cannot approve a renamed medication");
            } catch (RuntimeException expected) {
                // Atomic rejection, no partial completion or stock debit.
            }
            x.assertUnchanged(2);
            assertNotNull(DB.eventReminders().findById(x.reminder.getId()));
            assertEquals(2, ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                    x.context, x.patient, x.when, x.reminder.getId(), current));
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void medicineReassignedToAnotherPatientInvalidatesExistingNotification()
            throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture(true);
        Patient other = new Patient();
        other.setCode("ci-snapshot-other-medicine-owner-" + System.nanoTime());
        other.setName("Synthetic other medicine owner");
        try {
            assertEquals(1, DB.patients().create(other));
            String shown = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(shown);
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE Medicines SET Patient = ? WHERE _id = ?",
                    new Object[]{other.getId(), x.medicines[1].getId()});
            assertEquals(other.getId(), DB.medicines()
                    .findById(x.medicines[1].getId()).getPatient().getId());

            String current = IntakeSnapshot.fingerprint(x.visible(2), x.reminder);
            assertNotNull(current);
            assertFalse("Medicine owner is part of the immutable displayed selection",
                    shown.equals(current));
            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), shown);
                fail("The original patient's notification must reject a reassigned medicine");
            } catch (RuntimeException expected) {
                // The previously displayed patient/medicine link is stale.
            }
            x.assertUnchanged(2);
            assertNotNull(DB.eventReminders().findById(x.reminder.getId()));

            // Even a freshly computed fingerprint must never authorize a
            // current patient whose medicine belongs to somebody else.
            try {
                ScheduleUtils.instance().checkIntakeEventsWithSnapshot(
                        x.context, x.patient, x.when, x.reminder.getId(), current);
                fail("Fresh snapshot cannot bypass medicine ownership validation");
            } catch (RuntimeException expected) {
                // Commit-time clinical ownership still guards the medication.
            }
            x.assertUnchanged(2);
        } finally {
            x.cleanup();
            if (other.getId() != null && DB.patients().findById(other.getId()) != null) {
                DB.patients().remove(other);
            }
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
