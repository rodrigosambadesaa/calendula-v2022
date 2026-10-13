/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.util;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.joda.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Routine;
import static org.junit.Assert.*;

/**
 * All fixtures are local/synthetic; no healthcare-provider server is queried.
 */
@RunWith(AndroidJUnit4.class)
public class ProviderScheduleMappingAtomicitySmokeTest {
    @Test
    public void providerScheduleFailureCannotLeaveOrphanMedicineOrTimeRoutine()
            throws Exception {
        assertTrue(DB.initialized);
        Patient patient = new Patient();
        patient.setName("Synthetic provider-mapping owner");
        patient.setCode("ci-provider-map-" + System.nanoTime());
        Medicine medicine = new Medicine("Synthetic provider medicine", Presentation.PILLS);
        Routine routine = new Routine(new LocalTime(6, 45), null);
        try {
            assertEquals(1, DB.patients().create(patient));
            medicine.setPatient(patient);
            routine.setPatient(patient);
            AtomicInteger attemptedRows = new AtomicInteger();
            boolean rejected = false;
            try {
                ScheduleCreator.inLocalMedicationTransaction(() -> {
                    assertEquals(1, DB.medicines().create(medicine));
                    attemptedRows.incrementAndGet();
                    assertEquals(1, DB.routines().create(routine));
                    attemptedRows.incrementAndGet();
                    // A later invalid official schedule/recurrence must roll
                    // back the medicine and unnamed routine created earlier.
                    throw new IllegalArgumentException(
                            "synthetic invalid provider recurrence");
                });
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Mapping must reject the invalid provider recurrence", rejected);
            assertEquals(2, attemptedRows.get());
            assertNotNull(medicine.getId());
            assertNotNull(routine.getId());
            assertNull("Orphan medicine must be rolled back",
                    DB.medicines().findById(medicine.getId()));
            assertNull("Unnamed time routine must be rolled back",
                    DB.routines().findById(routine.getId()));
            assertNotNull("Existing owner must survive mapping failure",
                    DB.patients().findById(patient.getId()));

            // The next valid mapping can reuse the same source identifiers;
            // rolled-back row IDs in Java objects must not be treated as
            // persisted records.
            Medicine retry = new Medicine("Synthetic provider medicine", Presentation.PILLS);
            retry.setPatient(patient);
            Routine retryTime = new Routine(new LocalTime(6, 45), null);
            retryTime.setPatient(patient);
            ScheduleCreator.inLocalMedicationTransaction(() -> {
                assertEquals(1, DB.medicines().create(retry));
                assertEquals(1, DB.routines().create(retryTime));
                return null;
            });
            assertNotNull(DB.medicines().findById(retry.getId()));
            assertNotNull(DB.routines().findById(retryTime.getId()));
        } finally {
            for (Medicine m : DB.medicines().findAll(patient.getId())) {
                DB.medicines().remove(m);
            }
            for (Routine rr : DB.routines().findAll(patient.getId())) {
                DB.routines().remove(rr);
            }
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }
}
