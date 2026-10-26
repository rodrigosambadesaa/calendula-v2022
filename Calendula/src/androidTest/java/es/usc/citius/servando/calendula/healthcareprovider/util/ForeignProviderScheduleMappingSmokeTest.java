package es.usc.citius.servando.calendula.healthcareprovider.util;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.util.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVisualizationType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import static org.junit.Assert.*;

/** Real database fixture: an Intent with another patient's ID must not schedule medication. */
@RunWith(AndroidJUnit4.class)
public class ForeignProviderScheduleMappingSmokeTest {
    @Test
    public void foreignProviderIdentifierIsRejectedBeforeMedicationScheduleMapping()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        String suffix = Long.toString(System.nanoTime());
        Patient a = patient("a-" + suffix), b = patient("b-" + suffix);
        DosageEntity dose = new DosageEntity();
        dose.setType(DosageType.AS_NEEDED);
        ActiveMedEntity foreign = new ActiveMedEntity();
        foreign.setCode("ci-foreign-map-" + suffix);
        foreign.setPatient(b);
        foreign.setDosage(dose);
        foreign.setType(ActiveMedType.DCPF);
        foreign.setVisualizationType(ActiveMedVisualizationType.INSULIN_TYPE);
        foreign.setDefaultDisplay("Synthetic provider prescription");
        try {
            assertEquals(1, DB.patients().create(a));
            assertEquals(1, DB.patients().create(b));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(dose));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(foreign));
            DB.patients().setActive(a);

            assertNull("Cannot read provider B as if it belonged to A",
                    ScheduleCreator.findProviderForActivePatient(foreign.getId()));
            Pair<ScheduleCreator.ScheduleMappingResult, Schedule> result =
                    ScheduleCreator.fromActiveMed(foreign.getId());
            assertEquals(ScheduleCreator.ScheduleMappingResult.NONE, result.first);
            assertNull("No local schedule can be mapped from a foreign medication", result.second);

            DB.patients().setActive(b);
            assertNotNull("Patient B can resolve their own provider record",
                    ScheduleCreator.findProviderForActivePatient(foreign.getId()));
            assertNull(ScheduleCreator.findProviderForActivePatient(null));
        } finally {
            if (previous != null && previous.getId() != null
                    && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit();
            }
            if (foreign.getId() != null
                    && DB.healthcareProviderDB().activeMeds().findById(foreign.getId()) != null)
                DB.healthcareProviderDB().activeMeds().deleteById(foreign.getId());
            if (dose.getId() != null && DB.healthcareProviderDB().dosages().findById(dose.getId()) != null)
                DB.healthcareProviderDB().dosages().deleteById(dose.getId());
            for (Patient owner : new Patient[]{a, b}) {
                if (owner.getId() != null && DB.patients().findById(owner.getId()) != null)
                    DB.patients().deleteById(owner.getId());
            }
        }
    }

    private static Patient patient(String suffix) {
        Patient p = new Patient();
        p.setCode("ci-schedule-owner-" + suffix);
        p.setName("Synthetic schedule owner");
        return p;
    }
}
