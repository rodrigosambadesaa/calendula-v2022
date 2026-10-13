/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.allergies.AllergenType;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Real SQLite patient-deletion regression with synthetic records only.
 * IDs greater than the JVM Long cache exercise distinct boxed owner objects.
 */
@RunWith(AndroidJUnit4.class)
public class PatientDeletionScopeSmokeTest {
    @Test
    public void missingOrUnsavedPatientCannotBeginCascadeErasure() throws Exception {
        assertTrue(DB.initialized);
        Patient survivor = new Patient();
        survivor.setCode("ci-invalid-delete-survivor-" + System.nanoTime());
        survivor.setName("Synthetic deletion guard survivor");
        Medicine protectedMedicine = new Medicine("Synthetic protected medication", Presentation.PILLS);
        try {
            assertEquals(1, DB.patients().create(survivor));
            protectedMedicine.setPatient(survivor);
            assertEquals(1, DB.medicines().create(protectedMedicine));
            Patient unsaved = new Patient();
            Patient negative = new Patient();
            negative.setId(-7L);
            Patient missing = new Patient();
            missing.setId(Long.MAX_VALUE - 900L);
            for (Patient invalid : new Patient[]{null, unsaved, negative, missing}) {
                boolean cascadeRejected = false;
                try {
                    DB.patients().removeCascade(invalid);
                } catch (IllegalArgumentException expected) {
                    cascadeRejected = true;
                }
                assertTrue("Cascade erasure needs a positive live patient ID",
                        cascadeRejected);
                boolean partialRejected = false;
                try {
                    DB.patients().removeAllStuff(invalid);
                } catch (IllegalArgumentException expected) {
                    partialRejected = true;
                }
                assertTrue("Partial erasure needs the same verified owner",
                        partialRejected);
            }
            assertNotNull("Unrelated patient must survive invalid erasures",
                    DB.patients().findById(survivor.getId()));
            assertNotNull("Their medication must remain untouched",
                    DB.medicines().findById(protectedMedicine.getId()));
        } finally {
            if (protectedMedicine.getId() != null
                    && DB.medicines().findById(protectedMedicine.getId()) != null) {
                DB.medicines().remove(protectedMedicine);
            }
            if (survivor.getId() != null
                    && DB.patients().findById(survivor.getId()) != null) {
                DB.patients().remove(survivor);
            }
        }
    }

    @Test
    public void deletingPatientRemovesOnlyTheirMedicationsAndRoutines() throws Exception {
        assertTrue(DB.initialized);
        final long seed = 2000000000L + (System.nanoTime() & 0x3fffffffL);
        final long deletedId = seed;
        final long retainedId = seed + 1L;
        final String deletedCode = "ci-deletion-A-" + seed;
        final String retainedCode = "ci-deletion-B-" + seed;
        Patient deleted = null;
        Patient retained = null;
        Medicine deletedMed = null;
        Medicine retainedMed = null;
        Routine deletedRoutine = null;
        Routine retainedRoutine = null;
        PatientAllergen deletedAllergen = null;
        PatientAllergen retainedAllergen = null;
        try {
            // Seed explicit non-interned Long IDs. Auto-generated test IDs near
            // 1 can mask the legacy boxed-object reference comparison.
            DB.patients().executeRaw(
                    "INSERT INTO Patients (_id, Code, Name) VALUES (?, ?, ?)",
                    String.valueOf(deletedId), deletedCode, "Synthetic patient A");
            DB.patients().executeRaw(
                    "INSERT INTO Patients (_id, Code, Name) VALUES (?, ?, ?)",
                    String.valueOf(retainedId), retainedCode, "Synthetic patient B");
            deleted = DB.patients().findById(deletedId);
            retained = DB.patients().findById(retainedId);
            assertNotNull(deleted);
            assertNotNull(retained);

            deletedMed = new Medicine("Synthetic medicine A", Presentation.PILLS);
            deletedMed.setPatient(deleted);
            DB.medicines().create(deletedMed);
            retainedMed = new Medicine("Synthetic medicine B", Presentation.PILLS);
            retainedMed.setPatient(retained);
            DB.medicines().create(retainedMed);

            deletedRoutine = new Routine(deleted, new LocalTime(7, 17), "Synthetic routine A");
            DB.routines().create(deletedRoutine);
            retainedRoutine = new Routine(retained, new LocalTime(7, 19), "Synthetic routine B");
            DB.routines().create(retainedRoutine);

            // Both patients carry the same synthetic allergy. Patient deletion
            // must erase only the deleted owner's private allergy record.
            deletedAllergen = new PatientAllergen("Synthetic ingredient",
                    AllergenType.ACTIVE_INGREDIENT, "SYNTHETIC-ID-ALLERGY",
                    deleted);
            retainedAllergen = new PatientAllergen("Synthetic ingredient",
                    AllergenType.ACTIVE_INGREDIENT, "SYNTHETIC-ID-ALLERGY",
                    retained);
            DB.patientAllergens().create(deletedAllergen);
            DB.patientAllergens().create(retainedAllergen);

            assertNotNull(DB.patientAllergens().findById(deletedAllergen.getId()));
            assertNotNull(DB.patientAllergens().findById(retainedAllergen.getId()));
            assertNotNull(DB.medicines().findById(deletedMed.getId()));
            assertNotNull(DB.routines().findById(deletedRoutine.getId()));
            DB.patients().removeCascade(DB.patients().findById(deletedId));

            assertNull("Removed patient's record must be deleted",
                    DB.patients().findById(deletedId));
            assertNull("Removed patient's medicine must not remain orphaned",
                    DB.medicines().findById(deletedMed.getId()));
            assertNull("Removed patient's routine must not remain orphaned",
                    DB.routines().findById(deletedRoutine.getId()));
            assertNull("Removed patient's allergy must not remain orphaned",
                    DB.patientAllergens().findById(deletedAllergen.getId()));
            assertNotNull("Other patient's record must survive",
                    DB.patients().findById(retainedId));
            assertNotNull("Other patient's medicine must survive",
                    DB.medicines().findById(retainedMed.getId()));
            assertNotNull("Other patient's routine must survive",
                    DB.routines().findById(retainedRoutine.getId()));
            assertNotNull("Other patient's identical allergy must survive",
                    DB.patientAllergens().findById(retainedAllergen.getId()));
        } finally {
            if (deletedAllergen != null && deletedAllergen.getId() != null
                    && DB.patientAllergens().findById(deletedAllergen.getId()) != null) {
                DB.patientAllergens().remove(deletedAllergen);
            }
            if (retainedAllergen != null && retainedAllergen.getId() != null
                    && DB.patientAllergens().findById(retainedAllergen.getId()) != null) {
                DB.patientAllergens().remove(retainedAllergen);
            }
            if (deletedMed != null && deletedMed.getId() != null
                    && DB.medicines().findById(deletedMed.getId()) != null) {
                DB.medicines().remove(deletedMed);
            }
            if (retainedMed != null && retainedMed.getId() != null
                    && DB.medicines().findById(retainedMed.getId()) != null) {
                DB.medicines().remove(retainedMed);
            }
            if (deletedRoutine != null && deletedRoutine.getId() != null
                    && DB.routines().findById(deletedRoutine.getId()) != null) {
                DB.routines().remove(deletedRoutine);
            }
            if (retainedRoutine != null && retainedRoutine.getId() != null
                    && DB.routines().findById(retainedRoutine.getId()) != null) {
                DB.routines().remove(retainedRoutine);
            }
            if (deleted != null && DB.patients().findById(deletedId) != null) {
                DB.patients().remove(deleted);
            }
            if (retained != null && DB.patients().findById(retainedId) != null) {
                DB.patients().remove(retained);
            }
        }
    }
    @Test
    public void deletingPatientErasesDecidedHistoryAndRemindersWithoutTouchingOtherOwner()
            throws Exception {
        assertTrue(DB.initialized);
        Patient deleted = new Patient();
        deleted.setCode("ci-history-delete-A-" + System.nanoTime());
        deleted.setName("Synthetic deleted patient");
        Patient retained = new Patient();
        retained.setCode("ci-history-delete-B-" + System.nanoTime());
        retained.setName("Synthetic retained patient");
        DateTime time = DateTime.now().plusHours(6).withMillisOfSecond(0);
        EventInstance takenA = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventInstance cancelledA = new EventInstance(time.plusMinutes(2),
                EventType.MEDICATION_INTAKE);
        EventInstance pendingB = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventReminder reminderA = new EventReminder(time, EventType.MEDICATION_INTAKE);
        EventReminder reminderB = new EventReminder(time, EventType.MEDICATION_INTAKE);
        try {
            assertEquals(1, DB.patients().create(deleted));
            assertEquals(1, DB.patients().create(retained));
            takenA.setPatient(deleted);
            takenA.setRef(-90000011L);
            takenA.addParam(EventInstance.PARAM_DOSE, 2.0d);
            takenA.setCompleted(true);
            takenA.setCompletedAt(DateTime.now().minusMinutes(5));
            cancelledA.setPatient(deleted);
            cancelledA.setRef(-90000012L);
            cancelledA.addParam(EventInstance.PARAM_DOSE, 3.0d);
            cancelledA.setCancelled(true);
            pendingB.setPatient(retained);
            pendingB.setRef(-90000013L);
            pendingB.addParam(EventInstance.PARAM_DOSE, 1.0d);
            for (EventInstance e : new EventInstance[]{takenA, cancelledA, pendingB}) {
                assertEquals(1, DB.eventInstances().create(e));
                assertNotNull(e.getId());
            }
            reminderA.setPatient(deleted);
            reminderA.setNextTime(time);
            reminderB.setPatient(retained);
            reminderB.setNextTime(time);
            assertEquals(1, DB.eventReminders().create(reminderA));
            assertEquals(1, DB.eventReminders().create(reminderB));

            DB.patients().removeCascade(deleted);
            assertNull("Deleted patient must not remain", DB.patients().findById(deleted.getId()));
            assertNull("Completed historical event must be erased",
                    DB.eventInstances().findById(takenA.getId()));
            assertNull("Cancelled historical event must be erased",
                    DB.eventInstances().findById(cancelledA.getId()));
            assertNull("Deleted patient's reminder must be revoked",
                    DB.eventReminders().findById(reminderA.getId()));
            assertNotNull("Unrelated patient must be preserved",
                    DB.patients().findById(retained.getId()));
            assertFalse("Other patient's pending intake must not be completed",
                    DB.eventInstances().findById(pendingB.getId()).completed());
            assertNotNull("Other patient's reminder must remain",
                    DB.eventReminders().findById(reminderB.getId()));
        } finally {
            for (EventReminder reminder : new EventReminder[]{reminderA, reminderB}) {
                if (reminder.getId() != null
                        && DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            for (EventInstance event : new EventInstance[]{takenA, cancelledA, pendingB}) {
                if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            for (Patient patient : new Patient[]{deleted, retained}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }

    @Test
    public void patientHistoryDeleteFailureRollsBackWithoutTouchingAnotherPatient()
            throws Exception {
        assertTrue(DB.initialized);
        Patient deleted = new Patient();
        deleted.setCode("ci-history-rollback-A-" + System.nanoTime());
        deleted.setName("Synthetic history rollback patient");
        Patient retained = new Patient();
        retained.setCode("ci-history-rollback-B-" + System.nanoTime());
        retained.setName("Synthetic history retained patient");
        DateTime time = DateTime.now().minusDays(2);
        EventInstance first = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventInstance second = new EventInstance(time.plusMinutes(1), EventType.MEDICATION_INTAKE);
        EventInstance other = new EventInstance(time, EventType.MEDICATION_INTAKE);
        android.database.sqlite.SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_patient_history_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(deleted));
            assertEquals(1, DB.patients().create(retained));
            first.setPatient(deleted);
            second.setPatient(deleted);
            other.setPatient(retained);
            for (EventInstance e : new EventInstance[]{first, second, other}) {
                assertEquals(1, DB.eventInstances().create(e));
            }
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventInstances WHEN OLD._id = " + second.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic patient history failure'); END;");
            boolean aborted = false;
            try {
                DB.eventInstances().removeAllForPatient(deleted);
            } catch (RuntimeException expected) {
                aborted = true;
            }
            assertTrue("Failure must reject patient-scoped history deletion", aborted);
            assertNotNull("First historical event must survive rolled-back batch",
                    DB.eventInstances().findById(first.getId()));
            assertNotNull("Second historical event must survive rolled-back batch",
                    DB.eventInstances().findById(second.getId()));
            assertNotNull("Other patient's event must never be touched",
                    DB.eventInstances().findById(other.getId()));
            assertNotNull("Failed history deletion cannot remove patient record",
                    DB.patients().findById(deleted.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            for (EventInstance e : new EventInstance[]{first, second, other}) {
                if (e.getId() != null && DB.eventInstances().findById(e.getId()) != null) {
                    DB.eventInstances().remove(e);
                }
            }
            for (Patient patient : new Patient[]{deleted, retained}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }

    /**
     * Opening or editing one patient's schedule must never prune another
     * patient's unnamed automatic routines. Verified on real Android SQLite.
     */
    @Test
    public void autoRoutineCleanupIsScopedToSelectedPatient() throws Exception {
        assertTrue(DB.initialized);
        String seed = String.valueOf(System.nanoTime());
        Patient patientA = new Patient();
        patientA.setCode("ci-routine-cleanup-A-" + seed);
        patientA.setName("Synthetic cleanup patient A");
        Patient patientB = new Patient();
        patientB.setCode("ci-routine-cleanup-B-" + seed);
        patientB.setName("Synthetic cleanup patient B");
        Routine aUnnamed = new Routine(patientA, new LocalTime(5, 12), null);
        Routine aNamed = new Routine(patientA, new LocalTime(5, 13),
                "Keep named routine A " + seed);
        Routine bUnnamed = new Routine(patientB, new LocalTime(5, 14), null);
        try {
            DB.patients().save(patientA);
            DB.patients().save(patientB);
            DB.routines().create(aUnnamed);
            DB.routines().create(aNamed);
            DB.routines().create(bUnnamed);

            assertTrue("A has one unnamed routine",
                    DB.routines().findWithoutName(patientA).size() == 1);
            assertTrue("B has one unnamed routine",
                    DB.routines().findWithoutName(patientB).size() == 1);
            assertTrue("No owner means no routine reads",
                    DB.routines().findWithoutName(null).isEmpty());
            assertTrue("Unsaved owner means no routine reads",
                    DB.routines().findWithoutName(new Patient()).isEmpty());

            ScheduleUtils.instance().removeUnlinkedAutoRoutines(patientA, false);

            assertNull("A's unlinked auto-created routine may be removed",
                    DB.routines().findById(aUnnamed.getId()));
            assertNotNull("A's named routine is not automatically deleted",
                    DB.routines().findById(aNamed.getId()));
            assertNotNull("B's unrelated orphan routine must survive A cleanup",
                    DB.routines().findById(bUnnamed.getId()));

            ScheduleUtils.instance().removeUnlinkedAutoRoutines(null, false);
            assertNotNull("No active patient may never trigger global cleanup",
                    DB.routines().findById(bUnnamed.getId()));
        } finally {
            for (Routine routine : new Routine[]{aUnnamed, aNamed, bUnnamed}) {
                if (routine.getId() != null
                        && DB.routines().findById(routine.getId()) != null) {
                    DB.routines().remove(routine);
                }
            }
            for (Patient patient : new Patient[]{patientA, patientB}) {
                if (patient.getId() != null
                        && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }

}
