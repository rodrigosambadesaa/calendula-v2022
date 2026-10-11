/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Real device SQLite regression: a notification's pending dose list must
 * agree with the delivery predicate and never contain previously handled
 * doses or events belonging to another patient. Synthetic records only.
 */
@RunWith(AndroidJUnit4.class)
public class PendingDoseSelectionSmokeTest {

    @Test
    public void editingScheduleDoseCannotRewriteDecidedMedicationHistory() {
        assertTrue(DB.initialized);
        DateTime future = DateTime.now().plusHours(7).withMillisOfSecond(0);
        Long syntheticScheduleRef = -System.nanoTime();
        EventInstance completed = new EventInstance(future, EventType.MEDICATION_INTAKE);
        completed.setRef(syntheticScheduleRef);
        completed.addParam(EventInstance.PARAM_DOSE, 1.0d);
        completed.setCompleted(true);
        DateTime completedAt = DateTime.now().minusMinutes(10).withMillisOfSecond(0);
        completed.setCompletedAt(completedAt);

        EventInstance cancelled = new EventInstance(future, EventType.MEDICATION_INTAKE);
        cancelled.setRef(syntheticScheduleRef);
        cancelled.addParam(EventInstance.PARAM_DOSE, 2.0d);
        cancelled.setCancelled(true);
        DateTime cancelledAt = completedAt.plusMinutes(1);
        cancelled.setCompletedAt(cancelledAt);

        EventInstance pending = new EventInstance(future, EventType.MEDICATION_INTAKE);
        pending.setRef(syntheticScheduleRef);
        pending.addParam(EventInstance.PARAM_DOSE, 3.0d);

        EventInstance[] fixtures = {completed, cancelled, pending};
        try {
            for (EventInstance event : fixtures) {
                DB.eventInstances().save(event);
                assertNotNull(event.getId());
            }
            Bundle edited = new Bundle();
            edited.putString(EventInstance.PARAM_DOSE, "4.0");

            assertEquals("Only the active dose may adopt the edited prescription", 1,
                    DB.eventInstances().updateParams(
                            EventType.MEDICATION_INTAKE, future, syntheticScheduleRef, edited));

            EventInstance takenAfter = DB.eventInstances().findById(completed.getId());
            EventInstance skippedAfter = DB.eventInstances().findById(cancelled.getId());
            EventInstance pendingAfter = DB.eventInstances().findById(pending.getId());
            assertEquals("The historical taken dose must remain unchanged",
                    1.0d, takenAfter.getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertEquals("The historical skipped dose must remain unchanged",
                    2.0d, skippedAfter.getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertEquals("Only the future unhandled dose receives edited parameters",
                    4.0d, pendingAfter.getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertTrue(takenAfter.completed());
            assertTrue(skippedAfter.cancelled());
            assertEquals(completedAt, takenAfter.completedAt());
            assertEquals(cancelledAt, skippedAfter.completedAt());
            assertFalse(pendingAfter.completed());
            assertFalse(pendingAfter.cancelled());
        } finally {
            for (EventInstance event : fixtures) {
                if (event.getId() != null
                        && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
        }
    }

    @Test
    public void editingRecurrenceDeletesOnlyObsoletePendingEvents() {
        assertTrue(DB.initialized);
        DateTime base = DateTime.now().plusHours(8).withMillisOfSecond(0);
        DateTime cutoff = DateTime.now();
        Long syntheticScheduleRef = -System.nanoTime();

        EventInstance pendingObsolete = new EventInstance(base, EventType.MEDICATION_INTAKE);
        EventInstance alreadyTaken = new EventInstance(
                base.plusMinutes(1), EventType.MEDICATION_INTAKE);
        EventInstance alreadyCancelled = new EventInstance(
                base.plusMinutes(2), EventType.MEDICATION_INTAKE);
        EventInstance retainedPending = new EventInstance(
                base.plusMinutes(3), EventType.MEDICATION_INTAKE);
        EventInstance differentSchedule = new EventInstance(
                base, EventType.MEDICATION_INTAKE);

        for (EventInstance event : new EventInstance[]{
                pendingObsolete, alreadyTaken, alreadyCancelled, retainedPending}) {
            event.setRef(syntheticScheduleRef);
        }
        alreadyTaken.setCompleted(true);
        alreadyTaken.setCompletedAt(DateTime.now().minusMinutes(5));
        alreadyCancelled.setCancelled(true);
        alreadyCancelled.setCompletedAt(DateTime.now().minusMinutes(3));
        differentSchedule.setRef(syntheticScheduleRef - 1L);
        EventInstance[] fixtures = {
                pendingObsolete, alreadyTaken, alreadyCancelled,
                retainedPending, differentSchedule
        };
        try {
            for (EventInstance event : fixtures) {
                DB.eventInstances().save(event);
                assertNotNull(event.getId());
            }

            assertEquals("Only obsolete pending times may be removed", 1,
                    DB.eventInstances().removeDistinctTime(EventType.MEDICATION_INTAKE,
                            syntheticScheduleRef,
                            java.util.Collections.singletonList(retainedPending.getTime()),
                            cutoff));
            assertTrue(DB.eventInstances().findById(pendingObsolete.getId()) == null);
            assertNotNull(DB.eventInstances().findById(alreadyTaken.getId()));
            assertNotNull(DB.eventInstances().findById(alreadyCancelled.getId()));
            assertNotNull(DB.eventInstances().findById(retainedPending.getId()));
            assertNotNull("Different schedules must never be affected",
                    DB.eventInstances().findById(differentSchedule.getId()));

            assertEquals("Clearing a recurrence must also accept an empty time list", 1,
                    DB.eventInstances().removeDistinctTime(EventType.MEDICATION_INTAKE,
                            syntheticScheduleRef, java.util.Collections.emptyList(), cutoff));
            assertTrue(DB.eventInstances().findById(retainedPending.getId()) == null);
            EventInstance takenAfter = DB.eventInstances().findById(alreadyTaken.getId());
            EventInstance cancelledAfter = DB.eventInstances().findById(alreadyCancelled.getId());
            assertTrue("Taken history must survive a schedule edit", takenAfter.completed());
            assertTrue("Cancelled history must survive a schedule edit", cancelledAfter.cancelled());
            assertEquals(alreadyTaken.completedAt(), takenAfter.completedAt());
            assertEquals(alreadyCancelled.completedAt(), cancelledAfter.completedAt());
        } finally {
            for (EventInstance event : fixtures) {
                if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
        }
    }

    @Test
    public void blockingScheduleKeepsDecidedFutureHistory() {
        assertTrue(DB.initialized);
        DateTime base = DateTime.now().plusHours(9).withMillisOfSecond(0);
        DateTime boundary = DateTime.now();
        Long scheduleRef = -System.nanoTime();
        EventInstance taken = new EventInstance(base, EventType.MEDICATION_INTAKE);
        EventInstance cancelled = new EventInstance(
                base.plusMinutes(1), EventType.MEDICATION_INTAKE);
        EventInstance pending = new EventInstance(
                base.plusMinutes(2), EventType.MEDICATION_INTAKE);
        EventInstance pastPending = new EventInstance(
                boundary.minusDays(1), EventType.MEDICATION_INTAKE);
        EventInstance otherSchedule = new EventInstance(
                base.plusMinutes(2), EventType.MEDICATION_INTAKE);
        taken.setCompleted(true);
        cancelled.setCancelled(true);
        for (EventInstance event : new EventInstance[]{
                taken, cancelled, pending, pastPending}) {
            event.setRef(scheduleRef);
        }
        otherSchedule.setRef(scheduleRef - 1L);
        EventInstance[] fixtures = {
                taken, cancelled, pending, pastPending, otherSchedule
        };
        try {
            for (EventInstance event : fixtures) {
                DB.eventInstances().save(event);
                assertNotNull(event.getId());
            }
            assertEquals("Blocking removes only pending future events for this schedule", 1,
                    DB.eventInstances().removeByRefAndTime(scheduleRef, boundary));
            assertTrue(DB.eventInstances().findById(pending.getId()) == null);
            assertTrue(DB.eventInstances().findById(taken.getId()).completed());
            assertTrue(DB.eventInstances().findById(cancelled.getId()).cancelled());
            assertNotNull("Past pending events remain outside the requested cleanup window",
                    DB.eventInstances().findById(pastPending.getId()));
            assertNotNull("Another schedule must not be affected",
                    DB.eventInstances().findById(otherSchedule.getId()));
        } finally {
            for (EventInstance event : fixtures) {
                if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
        }
    }

    @Test
    public void pendingMedicationItemsExcludeCompletedCancelledAndOtherPatients() {
        assertTrue(DB.initialized);
        DateTime time = DateTime.now().plusHours(14).withMillisOfSecond(0);
        Patient a = new Patient();
        a.setCode("ci-pending-dose-a-" + System.nanoTime());
        a.setName("Synthetic pending dose A");
        Patient b = new Patient();
        b.setCode("ci-pending-dose-b-" + System.nanoTime());
        b.setName("Synthetic pending dose B");

        EventInstance activeA = new EventInstance(time, EventType.MEDICATION_INTAKE);
        activeA.setPatient(a);
        EventInstance completedA = new EventInstance(time, EventType.MEDICATION_INTAKE);
        completedA.setPatient(a);
        completedA.setCompleted(true);
        EventInstance cancelledA = new EventInstance(time, EventType.MEDICATION_INTAKE);
        cancelledA.setPatient(a);
        cancelledA.setCancelled(true);
        EventInstance activeB = new EventInstance(time, EventType.MEDICATION_INTAKE);
        activeB.setPatient(b);

        EventInstance[] fixtures = {activeA, completedA, cancelledA, activeB};
        try {
            DB.patients().save(a);
            DB.patients().save(b);
            for (EventInstance event : fixtures) {
                DB.eventInstances().save(event);
                assertNotNull(event.getId());
            }

            List<EventInstance> dueForA = DB.eventInstances().findPending(
                    EventType.MEDICATION_INTAKE, time, a);
            assertEquals("Only A's active dose may appear in A's notification",
                    1, dueForA.size());
            assertEquals(activeA.getId(), dueForA.get(0).getId());
            assertTrue(DB.eventInstances().existsPending(
                    EventType.MEDICATION_INTAKE, time, a));

            List<EventInstance> dueForB = DB.eventInstances().findPending(
                    EventType.MEDICATION_INTAKE, time, b);
            assertEquals("A's events must never appear in B's notification",
                    1, dueForB.size());
            assertEquals(activeB.getId(), dueForB.get(0).getId());

            activeA.setCompleted(true);
            DB.eventInstances().save(activeA);
            assertFalse("No remaining active dose for A should authorize delivery",
                    DB.eventInstances().existsPending(
                            EventType.MEDICATION_INTAKE, time, a));
            assertTrue("A completed/cancelled patient's dose list must be empty",
                    DB.eventInstances().findPending(
                            EventType.MEDICATION_INTAKE, time, a).isEmpty());
            assertEquals("B remains unaffected after A's completion",
                    activeB.getId(), DB.eventInstances().findPending(
                            EventType.MEDICATION_INTAKE, time, b).get(0).getId());
        } finally {
            for (EventInstance event : fixtures) {
                if (event.getId() != null
                        && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            if (a.getId() != null) {
                DB.patients().remove(a);
            }
            if (b.getId() != null) {
                DB.patients().remove(b);
            }
        }
    }

    @Test
    public void replayingAgendaGenerationCannotDuplicateOrRewriteTakenDose() {
        assertTrue(DB.initialized);
        DateTime slot = DateTime.now().plusHours(17).withMillisOfSecond(0);
        Long syntheticRef = -System.nanoTime();

        EventInstance original = new EventInstance(slot, EventType.MEDICATION_INTAKE);
        original.setRef(syntheticRef);
        original.addParam(EventInstance.PARAM_DOSE, 1.25d);
        original.setCompleted(true);
        DateTime takenAt = DateTime.now().minusMinutes(4).withMillisOfSecond(0);
        original.setCompletedAt(takenAt);

        EventInstance replay = new EventInstance(slot, EventType.MEDICATION_INTAKE);
        replay.setRef(syntheticRef);
        replay.addParam(EventInstance.PARAM_DOSE, 8.0d);
        EventInstance replayTwice = new EventInstance(slot, EventType.MEDICATION_INTAKE);
        replayTwice.setRef(syntheticRef);
        replayTwice.addParam(EventInstance.PARAM_DOSE, 9.0d);
        EventInstance newSlot = new EventInstance(slot.plusMinutes(1),
                EventType.MEDICATION_INTAKE);
        newSlot.setRef(syntheticRef);
        newSlot.addParam(EventInstance.PARAM_DOSE, 2.0d);
        try {
            Agenda.instance().addEvents(java.util.Collections.singletonList(original));
            assertNotNull(original.getId());
            Agenda.instance().addEvents(java.util.Arrays.asList(replay, replayTwice, newSlot));

            int sameSlotCount = 0;
            for (EventInstance event : DB.eventInstances().findByTypeAndTime(
                    EventType.MEDICATION_INTAKE, slot)) {
                if (syntheticRef.equals(event.getRef())) sameSlotCount++;
            }
            assertEquals("Replaying a known schedule/time may not insert another intake",
                    1, sameSlotCount);
            assertTrue("A genuinely new time must still be added", newSlot.getId() != null);
            EventInstance persisted = DB.eventInstances().findById(original.getId());
            assertTrue(persisted.completed());
            assertEquals(takenAt, persisted.completedAt());
            assertEquals("Newer prescription parameters must not rewrite a taken dose",
                    1.25d, persisted.getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertTrue("Replay objects should not get fresh SQL identities",
                    replay.getId() == null && replayTwice.getId() == null);
        } finally {
            for (EventInstance event : new EventInstance[]{original, replay, replayTwice, newSlot}) {
                if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
        }
    }

    @Test
    public void agendaEventBatchFailureRollsBackPreviouslyInsertedRows() {
        assertTrue(DB.initialized);
        DateTime slot = DateTime.now().plusHours(19).withMillisOfSecond(0);
        Long syntheticRef = -System.nanoTime();
        EventInstance candidate = new EventInstance(slot, EventType.MEDICATION_INTAKE);
        candidate.setRef(syntheticRef);
        try {
            boolean rejected = false;
            try {
                Agenda.instance().addEvents(java.util.Arrays.asList(candidate, null));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Malformed batch must abort rather than partially commit", rejected);
            assertFalse("An inserted row must be rolled back with the invalid batch",
                    DB.eventInstances().exists(
                            EventType.MEDICATION_INTAKE, slot, syntheticRef));
        } finally {
            if (candidate.getId() != null
                    && DB.eventInstances().findById(candidate.getId()) != null) {
                DB.eventInstances().remove(candidate);
            }
        }
    }

    @Test
    public void nullPatientSelectionCannotIncludeNamedPatientRecords() {
        assertTrue(DB.initialized);
        DateTime time = DateTime.now().plusHours(15).withMillisOfSecond(0);
        Patient named = new Patient();
        named.setCode("ci-pending-dose-named-" + System.nanoTime());
        named.setName("Synthetic named patient");
        EventInstance orphan = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventInstance namedEvent = new EventInstance(time, EventType.MEDICATION_INTAKE);
        namedEvent.setPatient(named);
        try {
            DB.patients().save(named);
            DB.eventInstances().save(orphan);
            DB.eventInstances().save(namedEvent);
            List<EventInstance> selected = DB.eventInstances().findPending(
                    EventType.MEDICATION_INTAKE, time, null);
            assertEquals("Only a null-patient row can match the null scope",
                    1, selected.size());
            assertEquals(orphan.getId(), selected.get(0).getId());
            assertTrue(DB.eventInstances().existsPending(
                    EventType.MEDICATION_INTAKE, time, null));
        } finally {
            for (EventInstance row : new EventInstance[]{orphan, namedEvent}) {
                if (row.getId() != null && DB.eventInstances().findById(row.getId()) != null) {
                    DB.eventInstances().remove(row);
                }
            }
            if (named.getId() != null) {
                DB.patients().remove(named);
            }
        }
    }
}
