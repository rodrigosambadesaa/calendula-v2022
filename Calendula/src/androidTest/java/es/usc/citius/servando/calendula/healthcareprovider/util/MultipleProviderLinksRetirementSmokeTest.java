package es.usc.citius.servando.calendula.healthcareprovider.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.joda.time.DateTime;
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
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import static org.junit.Assert.*;

/** Disposable SQLite fixtures only; no real prescriptions or patient data. */
@RunWith(AndroidJUnit4.class)
public class MultipleProviderLinksRetirementSmokeTest {
    @Test
    public void authoritativeRetirementReconcilesEveryOwnedLocalLink()
            throws Exception {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previouslyActive = DB.patients().getActive(ctx);
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        Object hashBefore = prefs.getAll().get(PreferenceKeys.REMOTE_LAST_HASH.key());
        Object ownerBefore = prefs.getAll().get(PreferenceKeys.REMOTE_LAST_HASH_OWNER.key());
        String nonce = Long.toString(System.nanoTime());
        Patient a = patient("a-" + nonce);
        Patient b = patient("b-" + nonce);
        DosageEntity doseA = dose();
        DosageEntity doseB = dose();
        ActiveMedEntity providerA = provider("ci-multiple-a-" + nonce, a, doseA);
        ActiveMedEntity providerB = provider("ci-multiple-b-" + nonce, b, doseB);
        Medicine local1 = local("First synthetic medicine", a, 5f);
        Medicine local2 = local("Second synthetic medicine", a, 8f);
        Schedule schedule1 = new Schedule(local1);
        Schedule schedule2 = new Schedule(local2);
        EventInstance upcoming1 = null, upcoming2 = null, confirmed = null;
        try {
            assertEquals(1, DB.patients().create(a));
            assertEquals(1, DB.patients().create(b));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(doseA));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(doseB));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(providerA));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(providerB));
            local1.setActiveMedId(providerA.getId());
            local2.setActiveMedId(providerA.getId());
            assertEquals(1, DB.medicines().create(local1));
            assertEquals(1, DB.medicines().create(local2));
            schedule1.setPatient(a);
            schedule1.setActiveMedId(providerA.getId());
            schedule2.setPatient(a);
            schedule2.setActiveMedId(providerA.getId());
            assertEquals(1, DB.schedules().create(schedule1));
            assertEquals(1, DB.schedules().create(schedule2));
            DateTime now = DateTime.now().withMillisOfSecond(0);
            upcoming1 = pending(now.plusDays(4), a, schedule1);
            upcoming2 = pending(now.plusDays(4), a, schedule2);
            assertEquals(1, DB.eventInstances().create(upcoming1));
            assertEquals(1, DB.eventInstances().create(upcoming2));
            confirmed = pending(now.minusDays(4), a, schedule2);
            confirmed.setCompleted(true);
            confirmed.setCompletedAt(now.minusDays(4).plusMinutes(4));
            assertEquals(1, DB.eventInstances().create(confirmed));

            DB.patients().setActive(a);
            ActiveMedUpdater.INSTANCE.onNewMeds(
                    "ci-multiple-retire-revision-" + nonce,
                    Collections.<ActiveMedVO>emptyList(), ctx, false);

            assertNull(DB.healthcareProviderDB().activeMeds().findById(providerA.getId()));
            assertNotNull("Another patient's official medication survives",
                    DB.healthcareProviderDB().activeMeds().findById(providerB.getId()));
            for (Schedule schedule : new Schedule[]{schedule1, schedule2}) {
                Schedule persisted = DB.schedules().findById(schedule.getId());
                assertNotNull("Local schedule must not be deleted", persisted);
                assertNull("Every official schedule link must be removed",
                        persisted.getActiveMedId());
                assertTrue("Every affected schedule must require review",
                        persisted.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_DELETE));
            }
            for (Medicine local : new Medicine[]{local1, local2}) {
                Medicine persisted = DB.medicines().findById(local.getId());
                assertNotNull("Local medicine must remain", persisted);
                assertNull("Every local medicine link must be removed",
                        persisted.getActiveMedId());
                assertEquals("Stock must remain unchanged",
                        local.getStock(), persisted.getStock());
            }
            assertNull(DB.eventInstances().findById(upcoming1.getId()));
            assertNull(DB.eventInstances().findById(upcoming2.getId()));
            EventInstance storedHistory = DB.eventInstances().findById(confirmed.getId());
            assertNotNull("Confirmed intake must survive", storedHistory);
            assertTrue(storedHistory.completed());
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            restore(restore, PreferenceKeys.REMOTE_LAST_HASH.key(), hashBefore);
            restore(restore, PreferenceKeys.REMOTE_LAST_HASH_OWNER.key(), ownerBefore);
            assertTrue(restore.commit());
            if (previouslyActive != null && previouslyActive.getId() != null
                    && DB.patients().findById(previouslyActive.getId()) != null)
                DB.patients().setActive(previouslyActive);
            else PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit();
            for (EventInstance e : new EventInstance[]{upcoming1, upcoming2, confirmed})
                if (e != null && e.getId() != null && DB.eventInstances().findById(e.getId()) != null)
                    DB.eventInstances().remove(e);
            for (Schedule s : new Schedule[]{schedule1, schedule2})
                if (s.getId() != null && DB.schedules().findById(s.getId()) != null)
                    DB.schedules().remove(s);
            for (Medicine m : new Medicine[]{local1, local2})
                if (m.getId() != null && DB.medicines().findById(m.getId()) != null)
                    DB.medicines().deleteById(m.getId());
            for (ActiveMedEntity official : new ActiveMedEntity[]{providerA, providerB})
                if (official.getId() != null
                        && DB.healthcareProviderDB().activeMeds().findById(official.getId()) != null)
                    DB.healthcareProviderDB().activeMeds().deleteById(official.getId());
            for (DosageEntity dosage : new DosageEntity[]{doseA, doseB})
                if (dosage.getId() != null && DB.healthcareProviderDB().dosages().findById(dosage.getId()) != null)
                    DB.healthcareProviderDB().dosages().deleteById(dosage.getId());
            for (Patient patient : new Patient[]{a, b})
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null)
                    DB.patients().deleteById(patient.getId());
        }
    }

    private static Patient patient(String name) {
        Patient p = new Patient();
        p.setCode("ci-multiple-owner-" + name);
        p.setName("Synthetic provider link owner");
        return p;
    }
    private static DosageEntity dose() {
        DosageEntity d = new DosageEntity();
        d.setType(DosageType.AS_NEEDED);
        return d;
    }
    private static ActiveMedEntity provider(String code, Patient patient, DosageEntity dosage) {
        ActiveMedEntity m = new ActiveMedEntity();
        m.setCode(code);
        m.setPatient(patient);
        m.setDosage(dosage);
        m.setType(ActiveMedType.DCPF);
        m.setVisualizationType(ActiveMedVisualizationType.INSULIN_TYPE);
        m.setDefaultDisplay("Synthetic official medicine");
        return m;
    }
    private static Medicine local(String name, Patient patient, float stock) {
        Medicine m = new Medicine(name, Presentation.PILLS);
        m.setPatient(patient);
        m.setStock(stock);
        return m;
    }
    private static EventInstance pending(DateTime when, Patient patient, Schedule schedule) {
        EventInstance e = new EventInstance(when, EventType.MEDICATION_INTAKE);
        e.setPatient(patient);
        e.setRef(schedule.getId());
        e.addParam(EventInstance.PARAM_DOSE, 1.0d);
        return e;
    }
    private static void restore(SharedPreferences.Editor e, String key, Object original) {
        if (original == null) e.remove(key);
        else if (original instanceof String) e.putString(key, (String) original);
        else if (original instanceof Integer) e.putInt(key, (Integer) original);
        else if (original instanceof Long) e.putLong(key, (Long) original);
        else if (original instanceof Boolean) e.putBoolean(key, (Boolean) original);
        else if (original instanceof Float) e.putFloat(key, (Float) original);
        else throw new AssertionError("Unexpected preference type");
    }
}
