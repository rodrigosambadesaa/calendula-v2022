/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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
