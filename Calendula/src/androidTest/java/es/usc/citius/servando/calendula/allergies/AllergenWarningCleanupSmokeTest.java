/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.allergies;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import java.util.Arrays;
import java.util.Collections;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.alerts.AtomicMedicineAllergyEditor;

import static org.junit.Assert.*;

/** SQLite fixtures are unique synthetic medicines; no database reset. */
@RunWith(AndroidJUnit4.class)
public class AllergenWarningCleanupSmokeTest {
    @Test
    public void allergyDetailsUpdateChecksStableOwnerAndRollsBackSqlWriteFailure()
            throws Exception {
        assertTrue(DB.initialized);
        String seed = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-allergy-atomic-owner-" + seed);
        owner.setName("Synthetic alert update owner");
        Medicine medicine = new Medicine("Synthetic immutable-warning fixture",
                Presentation.PILLS);
        AllergenVO first = new AllergenVO(
                AllergenType.EXCIPIENT, "Synthetic original", "CI-ORIG-" + seed);
        AllergenVO replacement = new AllergenVO(
                AllergenType.EXCIPIENT, "Synthetic replacement", "CI-NEW-" + seed);
        AllergyPatientAlert saved = null;
        final String trigger = "ci_reject_allergy_warning_detail_update";
        final SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            saved = new AllergyPatientAlert(medicine, Collections.singletonList(first));
            assertEquals(1, DB.alerts().create(saved));
            assertNotNull(saved.id());
            final Long alertId = saved.id();
            final String original = DB.alerts().findById(alertId).getJsonDetails();

            AllergyPatientAlert revised =
                    (AllergyPatientAlert) DB.alerts().findById(alertId).map();
            revised.setDetails(new AllergyPatientAlert.AllergyAlertInfo(
                    Collections.singletonList(replacement)));
            Patient forged = new Patient();
            forged.setId(owner.getId());
            forged.setCode("ci-stale-recycled-owner-" + seed);
            revised.setPatient(forged);
            boolean rejected = false;
            try {
                AllergyAlertUtil.updateExistingAllergyWarning(revised);
            } catch (Exception expected) {
                rejected = true;
            }
            assertTrue("A recycled owner code cannot change a HIGH warning", rejected);
            assertEquals("Rejected identity must preserve original warning",
                    original, DB.alerts().findById(alertId).getJsonDetails());

            revised.setPatient(owner);
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON PatientAlerts WHEN OLD._id = " + alertId
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic allergy update failure'); END;");
            rejected = false;
            try {
                AllergyAlertUtil.updateExistingAllergyWarning(revised);
            } catch (Exception expected) {
                rejected = true;
            }
            assertTrue("SQLite write failure must be propagated, not silently accepted",
                    rejected);
            assertEquals("Failed SQL update must not alter the original warning",
                    original, DB.alerts().findById(alertId).getJsonDetails());

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            AllergyAlertUtil.updateExistingAllergyWarning(revised);
            assertEquals("Successful retry must update the actual stored warning",
                    revised.getJsonDetails(),
                    DB.alerts().findById(alertId).getJsonDetails());
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (saved != null && saved.id() != null
                    && DB.alerts().findById(saved.id()) != null) {
                DB.alerts().deleteById(saved.id());
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().deleteById(medicine.getId());
            }
            if (owner.getId() != null
                    && DB.patients().findById(owner.getId()) != null) {
                DB.patients().deleteById(owner.getId());
            }
        }
    }

    @Test
    public void removingAllergenDeletesEmptyWarningButPreservesOtherWarningIngredients()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String seed = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-allergen-cleanup-" + seed);
        owner.setName("Synthetic allergen owner");
        Medicine onlyA = new Medicine("Synthetic one-allergen medicine", Presentation.PILLS);
        Medicine alsoB = new Medicine("Synthetic two-allergen medicine", Presentation.PILLS);
        AllergenVO a = new AllergenVO(AllergenType.EXCIPIENT, "Synthetic alpha", "CI-A-" + seed);
        AllergenVO b = new AllergenVO(AllergenType.EXCIPIENT, "Synthetic beta", "CI-B-" + seed);
        AllergyPatientAlert firstAlert = null;
        AllergyPatientAlert secondAlert = null;
        try {
            assertEquals(1, DB.patients().create(owner));
            onlyA.setPatient(owner);
            alsoB.setPatient(owner);
            assertEquals(1, DB.medicines().create(onlyA));
            assertEquals(1, DB.medicines().create(alsoB));
            firstAlert = new AllergyPatientAlert(onlyA, Collections.singletonList(a));
            secondAlert = new AllergyPatientAlert(alsoB, Arrays.asList(a, b));
            assertEquals(1, DB.alerts().create(firstAlert));
            assertEquals(1, DB.alerts().create(secondAlert));

            // Simulate an old queued profile callback after a database
            // restore has recycled this numeric patient ID for another code.
            // Neither the first HIGH warning nor the multi-allergen warning
            // may be mutated by that stale callback.
            Patient recycledId = new Patient();
            recycledId.setId(owner.getId());
            recycledId.setCode("ci-other-patient-after-restore-" + seed);
            PatientAllergen forgedRemoval = new PatientAllergen(a, recycledId);
            boolean rejected = false;
            try {
                AllergyAlertUtil.removeAllergyAlerts(context, forgedRemoval);
            } catch (RuntimeException | java.sql.SQLException expected) {
                rejected = true;
            }
            assertTrue("Same-ID/different-code allergen cleanup must fail closed", rejected);
            assertNotNull("Rejected callback must preserve first HIGH alert",
                    DB.alerts().findById(firstAlert.id()));
            assertEquals("Rejected callback must preserve second warning details",
                    2, ((AllergyPatientAlert) DB.alerts().findById(secondAlert.id()).map())
                            .getDetails().getAllergens().size());

            PatientAllergen removed = new PatientAllergen(a, owner);
            AllergyAlertUtil.removeAllergyAlerts(context, removed);

            assertNull("HIGH warning only about removed allergen must be removed",
                    DB.alerts().findById(firstAlert.id()));
            PatientAlert retained = DB.alerts().findById(secondAlert.id());
            assertNotNull("Other active allergen must retain its HIGH warning", retained);
            AllergyPatientAlert typed = (AllergyPatientAlert) retained.map();
            assertNotNull(typed.getDetails());
            assertEquals(1, typed.getDetails().getAllergens().size());
            assertEquals(b, typed.getDetails().getAllergens().get(0));
        } finally {
            for (AllergyPatientAlert alert : new AllergyPatientAlert[]{firstAlert, secondAlert}) {
                if (alert != null && alert.id() != null && DB.alerts().findById(alert.id()) != null)
                    DB.alerts().deleteById(alert.id());
            }
            for (Medicine medicine : new Medicine[]{onlyA, alsoB}) {
                if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null)
                    DB.medicines().deleteById(medicine.getId());
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null)
                DB.patients().deleteById(owner.getId());
        }
    }
    /**
     * Test a full medication edit: a later alert failure must roll back an
     * earlier medicine update, and the successful retry must commit both.
     * Use synthetic disposable rows and SQLite RAISE(ABORT) triggers only.
     */
    @Test
    public void medicineAndAllergyEditsRollbackOnEverySqlFailureAndRetry()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-atomic-med-owner-" + suffix);
        owner.setName("Synthetic atomic medicine owner");
        Medicine medicine = new Medicine("Synthetic old name", Presentation.PILLS);
        AllergenVO original = new AllergenVO(
                AllergenType.EXCIPIENT, "Synthetic original", "CI-OLD-" + suffix);
        AllergenVO replacement = new AllergenVO(
                AllergenType.EXCIPIENT, "Synthetic replacement", "CI-NEW-" + suffix);
        AllergyPatientAlert initial = null;
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_atomic_med_allergy_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            DB.patients().setActive(owner);
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            initial = new AllergyPatientAlert(
                    medicine, Collections.singletonList(original));
            assertEquals(1, DB.alerts().create(initial));
            String previousDetails = DB.alerts().findById(initial.id()).getJsonDetails();
            medicine.setName("Synthetic edited name");

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON Medicines WHEN OLD._id = "
                    + medicine.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'ci medicine write failure'); END;");
            assertAtomicEditRejected(context, medicine,
                    Collections.singletonList(replacement));
            assertEquals("Medicine SQL failure must leave old name",
                    "Synthetic old name", persistedName(medicine));
            assertEquals(previousDetails,
                    DB.alerts().findById(initial.id()).getJsonDetails());

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON PatientAlerts WHEN OLD._id = "
                    + initial.id()
                    + " BEGIN SELECT RAISE(ABORT, 'ci allergy update failure'); END;");
            assertAtomicEditRejected(context, medicine,
                    Collections.singletonList(replacement));
            assertEquals("Alert SQL failure must roll back earlier medicine UPDATE",
                    "Synthetic old name", persistedName(medicine));
            assertEquals("Alert SQL failure must preserve original clinical warning",
                    previousDetails, DB.alerts().findById(initial.id()).getJsonDetails());

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            AtomicMedicineAllergyEditor.edit(
                    context, medicine, Collections.singletonList(replacement));
            assertEquals("Synthetic edited name", persistedName(medicine));
            assertEquals(replacement,
                    ((AllergyPatientAlert) DB.alerts().findById(initial.id()).map())
                            .getDetails().getAllergens().get(0));

            // Removing an obsolete warning is part of the very same edit,
            // not a second independently committed alert cleanup.
            medicine.setName("Synthetic replacement prescription");
            medicine.setCn("ci-catalog-replacement-" + suffix);
            medicine.setDatabase("ci-synthetic-catalog");
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON PatientAlerts WHEN OLD._id = "
                    + initial.id()
                    + " BEGIN SELECT RAISE(ABORT, 'ci allergy retirement failure'); END;");
            assertAtomicEditRejected(context, medicine, null);
            assertEquals("Synthetic edited name", persistedName(medicine));
            assertNotNull("Failed warning removal must roll back medicine edit",
                    DB.alerts().findById(initial.id()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            AtomicMedicineAllergyEditor.edit(context, medicine, null);
            assertEquals("Synthetic replacement prescription", persistedName(medicine));
            assertNull(DB.alerts().findById(initial.id()));

            // A new HIGH warning must not survive failure AFTER the medicine
            // SQL UPDATE, and a later retry must be able to create it.
            medicine.setName("Synthetic newly warned medicine");
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON PatientAlerts WHEN NEW.Medicine = "
                    + medicine.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'ci new warning failure'); END;");
            assertAtomicEditRejected(context, medicine,
                    Collections.singletonList(original));
            assertEquals("Synthetic replacement prescription", persistedName(medicine));
            assertTrue(DB.alerts().findByMedicineAndType(medicine,
                    AllergyPatientAlert.class.getCanonicalName()).isEmpty());

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            AtomicMedicineAllergyEditor.edit(
                    context, medicine, Collections.singletonList(original));
            assertEquals("Synthetic newly warned medicine", persistedName(medicine));
            assertEquals(1, DB.alerts().findByMedicineAndType(
                    medicine, AllergyPatientAlert.class.getCanonicalName()).size());

            Patient recycledOwner = new Patient();
            recycledOwner.setId(owner.getId());
            recycledOwner.setCode("ci-another-owner-" + suffix);
            medicine.setPatient(recycledOwner);
            medicine.setName("Synthetic forbidden cross-patient edit");
            assertAtomicEditRejected(context, medicine, null);
            assertEquals("Synthetic newly warned medicine", persistedName(medicine));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (previous != null && previous.getId() != null
                    && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit()
                        .remove(PreferenceKeys.PATIENTS_ACTIVE.key())
                        .remove(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key())
                        .remove(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key())
                        .commit();
            }
            if (medicine.getId() != null && owner.getId() != null) {
                // Restore a usable owner for scoped fixture cleanup even if
                // the last test deliberately supplied a recycled identity.
                medicine.setPatient(owner);
                for (PatientAlert alert : DB.alerts().findByMedicineAndType(
                        medicine, AllergyPatientAlert.class.getCanonicalName())) {
                    DB.alerts().deleteById(alert.id());
                }
                if (DB.medicines().findById(medicine.getId()) != null) {
                    DB.medicines().deleteById(medicine.getId());
                }
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().deleteById(owner.getId());
            }
        }
    }


    /**
     * A HIGH warning may block several scheduled doses. A failed second
     * occurrence DELETE must roll back the medicine edit, new HIGH warning,
     * earlier schedule block, and removed future occurrences as ONE unit.
     */
    @Test
    public void failedSecondScheduleBlockRollsBackMedicineAndWarningToo()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-edit-block-owner-" + suffix);
        owner.setName("Synthetic atomic scheduling owner");
        Medicine medicine = new Medicine("Synthetic unchanged medicine", Presentation.PILLS);
        Schedule first = null;
        Schedule second = null;
        EventInstance e1 = null;
        EventInstance e2 = null;
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        String trigger = "ci_edit_block_second_event_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            DB.patients().setActive(owner);
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
            e1 = new EventInstance(DateTime.now().plusMinutes(25), EventType.MEDICATION_INTAKE);
            e2 = new EventInstance(DateTime.now().plusMinutes(30), EventType.MEDICATION_INTAKE);
            e1.setPatient(owner);
            e2.setPatient(owner);
            e1.setRef(first.getId());
            e2.setRef(second.getId());
            e1.addParam(EventInstance.PARAM_DOSE, 1.0d);
            e2.addParam(EventInstance.PARAM_DOSE, 1.0d);
            assertEquals(1, DB.eventInstances().create(e1));
            assertEquals(1, DB.eventInstances().create(e2));
            medicine.setName("Synthetic changed medicine");
            AllergenVO verified = new AllergenVO(
                    AllergenType.EXCIPIENT, "Synthetic guarded allergen", "CI-BLOCK-" + suffix);

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventInstances WHEN OLD._id = " + e2.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'ci synthetic second intake failure'); END;");
            assertAtomicEditRejected(context, medicine, Collections.singletonList(verified));
            assertEquals("Synthetic unchanged medicine", persistedName(medicine));
            assertTrue("Rolled-back HIGH warning must not remain",
                    DB.alerts().findByMedicineAndType(medicine,
                            AllergyPatientAlert.class.getCanonicalName()).isEmpty());
            assertFalse(DB.schedules().findById(first.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            assertFalse(DB.schedules().findById(second.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            assertNotNull(DB.eventInstances().findById(e1.getId()));
            assertNotNull(DB.eventInstances().findById(e2.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            AtomicMedicineAllergyEditor.edit(
                    context, medicine, Collections.singletonList(verified));
            assertEquals("Synthetic changed medicine", persistedName(medicine));
            assertEquals(1, DB.alerts().findByMedicineAndType(
                    medicine, AllergyPatientAlert.class.getCanonicalName()).size());
            assertTrue(DB.schedules().findById(first.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            assertTrue(DB.schedules().findById(second.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            assertNull(DB.eventInstances().findById(e1.getId()));
            assertNull(DB.eventInstances().findById(e2.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (previous != null && previous.getId() != null
                    && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit()
                        .remove(PreferenceKeys.PATIENTS_ACTIVE.key())
                        .remove(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key())
                        .remove(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key())
                        .commit();
            }
            if (medicine.getId() != null && owner.getId() != null) {
                for (PatientAlert alert : DB.alerts().findByMedicineAndType(
                        medicine, AllergyPatientAlert.class.getCanonicalName())) {
                    DB.alerts().deleteById(alert.id());
                }
            }
            for (EventInstance event : new EventInstance[]{e1, e2}) {
                if (event != null && event.getId() != null
                        && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().deleteById(event.getId());
                }
            }
            for (Schedule schedule : new Schedule[]{first, second}) {
                if (schedule != null && schedule.getId() != null
                        && DB.schedules().findById(schedule.getId()) != null) {
                    DB.eventInstances().removeByRef(schedule.getId());
                    DB.schedules().deleteById(schedule.getId());
                }
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().deleteById(medicine.getId());
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().deleteById(owner.getId());
            }
        }
    }

    private static String persistedName(Medicine medicine) {
        Medicine current = DB.medicines().findById(medicine.getId());
        assertNotNull(current);
        assertEquals(1, DB.medicines().refresh(current));
        return current.getName();
    }

    private static void assertAtomicEditRejected(Context context, Medicine medicine,
                                                  java.util.List<AllergenVO> allergens) {
        boolean rejected = false;
        try {
            AtomicMedicineAllergyEditor.edit(context, medicine, allergens);
        } catch (RuntimeException expected) {
            rejected = true;
        }
        assertTrue("Failed SQL/owner preflight must reject the entire edit", rejected);
    }

}
