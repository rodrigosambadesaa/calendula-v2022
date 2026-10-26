package es.usc.citius.servando.calendula.fragments;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVisualizationType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import static org.junit.Assert.*;

/** Disposable, patient-owned SQLite fixtures only; no real clinical data. */
@RunWith(AndroidJUnit4.class)
public class OfficialReminderPickerIsolationSmokeTest {
    @Test
    public void selectsMultipleOwnedSchedulesButNeverCrossPatientCorruptedLinks()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        String suffix = Long.toString(System.nanoTime());
        Patient a = patient("a-" + suffix);
        Patient b = patient("b-" + suffix);
        DosageEntity dosage = new DosageEntity();
        dosage.setType(DosageType.AS_NEEDED);
        ActiveMedEntity provider = new ActiveMedEntity();
        provider.setCode("ci-reminder-provider-" + suffix);
        provider.setPatient(a);
        provider.setType(ActiveMedType.DCPF);
        provider.setVisualizationType(ActiveMedVisualizationType.INSULIN_TYPE);
        provider.setDefaultDisplay("Synthetic medication");
        provider.setDosage(dosage);
        Medicine first = new Medicine("Synthetic reminder one");
        first.setPatient(a);
        Medicine second = new Medicine("Synthetic reminder two");
        second.setPatient(a);
        Medicine foreign = new Medicine("Synthetic foreign reminder");
        foreign.setPatient(b);
        Schedule one = new Schedule(first);
        Schedule two = new Schedule(second);
        Schedule invalid = new Schedule(foreign);
        try {
            assertEquals(1, DB.patients().create(a));
            assertEquals(1, DB.patients().create(b));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(dosage));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(provider));
            for (Medicine medicine : new Medicine[]{first, second, foreign}) {
                assertEquals(1, DB.medicines().create(medicine));
            }
            for (Schedule schedule : new Schedule[]{one, two, invalid}) {
                schedule.setPatient(schedule == invalid ? b : a);
                schedule.setActiveMedId(provider.getId());
            }
            assertEquals(1, DB.schedules().create(one));
            assertEquals(1, DB.schedules().create(two));
            DB.patients().setActive(a);
            List<Schedule> links = DB.schedules().findBy(Schedule.BOUND_TO, provider.getId());
            assertEquals(OfficialReminderLinkSelector.Action.CHOOSE,
                    OfficialReminderLinkSelector.inspect(a.getId(), a.getId(),
                            a.getId(), links).action);
            assertEquals(2, links.size());

            assertEquals(OfficialReminderLinkSelector.Action.UNAVAILABLE,
                    OfficialReminderLinkSelector.inspect(a.getId(), b.getId(),
                            a.getId(), links).action);

            assertEquals(1, DB.schedules().create(invalid));
            links = DB.schedules().findBy(Schedule.BOUND_TO, provider.getId());
            assertEquals(OfficialReminderLinkSelector.Action.UNAVAILABLE,
                    OfficialReminderLinkSelector.inspect(a.getId(), a.getId(),
                            a.getId(), links).action);
            assertNotNull(DB.schedules().findById(one.getId()));
            assertNotNull(DB.schedules().findById(two.getId()));
            assertNotNull(DB.schedules().findById(invalid.getId()));
        } finally {
            if (previous != null && previous.getId() != null
                    && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit();
            }
            for (Schedule schedule : new Schedule[]{one, two, invalid}) {
                if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null)
                    DB.schedules().remove(schedule);
            }
            for (Medicine medicine : new Medicine[]{first, second, foreign}) {
                if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null)
                    DB.medicines().deleteById(medicine.getId());
            }
            if (provider.getId() != null
                    && DB.healthcareProviderDB().activeMeds().findById(provider.getId()) != null)
                DB.healthcareProviderDB().activeMeds().deleteById(provider.getId());
            if (dosage.getId() != null
                    && DB.healthcareProviderDB().dosages().findById(dosage.getId()) != null)
                DB.healthcareProviderDB().dosages().deleteById(dosage.getId());
            for (Patient patient : new Patient[]{a, b}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null)
                    DB.patients().deleteById(patient.getId());
            }
        }
    }

    private static Patient patient(String id) {
        Patient p = new Patient();
        p.setCode("ci-reminder-owner-" + id);
        p.setName("Synthetic reminder owner");
        return p;
    }
}
