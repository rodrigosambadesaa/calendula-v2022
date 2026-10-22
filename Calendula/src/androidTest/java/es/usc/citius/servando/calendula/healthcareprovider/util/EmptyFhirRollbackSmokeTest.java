package es.usc.citius.servando.calendula.healthcareprovider.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVisualizationType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import org.joda.time.DateTime;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import static org.junit.Assert.*;

/** SQLite injected failure only; tests atomic retirement and revision denial. */
@RunWith(AndroidJUnit4.class)
public class EmptyFhirRollbackSmokeTest {
    @Test
    public void failedProviderRetirementRollsBackMedicineUnlinkAndRevision() throws Exception {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        Patient previous = DB.patients().getActive(ctx);
        String hashKey = PreferenceKeys.REMOTE_LAST_HASH.key();
        String ownerKey = PreferenceKeys.REMOTE_LAST_HASH_OWNER.key();
        Object originalHash = prefs.getAll().get(hashKey);
        Object originalOwner = prefs.getAll().get(ownerKey);
        Patient patient = new Patient();
        patient.setCode("ci-fhir-rollback-" + System.nanoTime());
        patient.setName("Synthetic rollback patient");
        DosageEntity dose = new DosageEntity();
        dose.setType(DosageType.AS_NEEDED);
        ActiveMedEntity official = new ActiveMedEntity();
        official.setCode("ci-rollback-med-" + System.nanoTime());
        official.setPatient(patient);
        official.setType(ActiveMedType.DCPF);
        official.setVisualizationType(ActiveMedVisualizationType.INSULIN_TYPE);
        official.setDefaultDisplay("Synthetic provider medication");
        official.setDosage(dose);
        Medicine local = new Medicine("Synthetic linked medicine", Presentation.PILLS);
        Schedule linkedSchedule = new Schedule(local);
        EventInstance future = null;
        EventReminder pendingAlarm = null;
        final String trigger = "ci_empty_fhir_retire_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(patient));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(dose));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(official));
            local.setPatient(patient);
            local.setStock(9f);
            local.setActiveMedId(official.getId());
            assertEquals(1, DB.medicines().create(local));
            linkedSchedule.setPatient(patient);
            linkedSchedule.setActiveMedId(official.getId());
            assertEquals(1, DB.schedules().create(linkedSchedule));
            future = new EventInstance(DateTime.now().plusDays(4), EventType.MEDICATION_INTAKE);
            future.setPatient(patient);
            future.setRef(linkedSchedule.getId());
            future.addParam(EventInstance.PARAM_DOSE, 1.0d);
            assertEquals(1, DB.eventInstances().create(future));
            pendingAlarm = new EventReminder(future.getTime(), EventType.MEDICATION_INTAKE);
            pendingAlarm.setPatient(patient);
            pendingAlarm.setNextTime(future.getTime());
            assertEquals(1, DB.eventReminders().create(pendingAlarm));
            DB.patients().setActive(patient);
            sqlite.execSQL("CREATE TRIGGER " + trigger
                + " BEFORE DELETE ON ActiveMedItems WHEN OLD._id = " + official.getId()
                + " BEGIN SELECT RAISE(ABORT, 'synthetic provider retirement failure'); END;");
            String rejectedRevision = "ci-must-not-acknowledge-" + System.nanoTime();
            try {
                ActiveMedUpdater.INSTANCE.onNewMeds(rejectedRevision,
                    Collections.<ActiveMedVO>emptyList(), ctx, false);
                fail("Failed SQLite retirement must reject the whole provider snapshot");
            } catch (RuntimeException expected) {
                // The transaction must undo earlier dosage/unlink writes.
            }
            assertNotNull("Provider row is retained after failed deletion",
                DB.healthcareProviderDB().activeMeds().findById(official.getId()));
            assertNotNull("Dosage must be restored by transaction rollback",
                DB.healthcareProviderDB().dosages().findById(dose.getId()));
            Medicine persisted = DB.medicines().findById(local.getId());
            assertNotNull(persisted);
            assertEquals("Local medicine link must survive a failed reconciliation",
                official.getId(), persisted.getActiveMedId());
            assertEquals("Local stock must never change", Float.valueOf(9f),
                persisted.getStock());
            assertFalse("Rejected revision must not be acknowledged",
                rejectedRevision.equals(prefs.getAll().get(hashKey)));
            Schedule afterFailure = DB.schedules().findById(linkedSchedule.getId());
            assertNotNull(afterFailure);
            assertEquals("A failed import cannot detach a schedule",
                official.getId(), afterFailure.getActiveMedId());
            assertFalse("Pending-review state must roll back with SQLite",
                afterFailure.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_DELETE));
            assertNotNull("Uncommitted removal must not erase a future intake",
                DB.eventInstances().findById(future.getId()));
            assertNotNull("An aborted import must not revoke its alarm reminder",
                DB.eventReminders().findById(pendingAlarm.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            SharedPreferences.Editor editor = prefs.edit();
            restore(editor, hashKey, originalHash);
            restore(editor, ownerKey, originalOwner);
            assertTrue(editor.commit());
            if (previous != null && previous.getId() != null
                    && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit();
            }
            if (pendingAlarm != null && pendingAlarm.getId() != null
                    && DB.eventReminders().findById(pendingAlarm.getId()) != null)
                DB.eventReminders().deleteById(pendingAlarm.getId());
            if (future != null && future.getId() != null
                    && DB.eventInstances().findById(future.getId()) != null)
                DB.eventInstances().remove(future);
            if (linkedSchedule.getId() != null
                    && DB.schedules().findById(linkedSchedule.getId()) != null)
                DB.schedules().remove(linkedSchedule);
            if (local.getId() != null && DB.medicines().findById(local.getId()) != null)
                DB.medicines().deleteById(local.getId());
            if (official.getId() != null
                    && DB.healthcareProviderDB().activeMeds().findById(official.getId()) != null)
                DB.healthcareProviderDB().activeMeds().deleteById(official.getId());
            if (dose.getId() != null && DB.healthcareProviderDB().dosages().findById(dose.getId()) != null)
                DB.healthcareProviderDB().dosages().deleteById(dose.getId());
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null)
                DB.patients().deleteById(patient.getId());
        }
    }

    private static void restore(SharedPreferences.Editor editor, String key, Object value) {
        if (value == null) editor.remove(key);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else throw new AssertionError("Unexpected original preference type");
    }
}
