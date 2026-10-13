/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.persistence.alerts.DrivingCautionAlert;
import es.usc.citius.servando.calendula.persistence.alerts.StockRunningOutAlert;

import static org.junit.Assert.*;

/** Only the three built-in alert models may be reconstructed from persisted type strings. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@SuppressWarnings({"rawtypes", "unchecked"})
public class PatientAlertTypeMappingTest {

    @Test
    public void allSupportedAlertsMapWithoutReflectionAndRetainStoredIdentity() {
        Patient owner = new Patient();
        owner.setId(470101L);
        Medicine medicine = new Medicine();
        medicine.setId(470102L);
        Class<?>[] supported = {
                AllergyPatientAlert.class, DrivingCautionAlert.class,
                StockRunningOutAlert.class
        };
        for (Class<?> supportedClass : supported) {
            PatientAlert stored = new PatientAlert();
            // The no-argument ORM constructors of Driving/Stock alerts
            // intentionally leave Type empty until persistence rehydrates it.
            stored.setType(supportedClass.getCanonicalName());
            stored.setId(470103L);
            stored.setPatient(owner);
            stored.setMedicine(medicine);
            stored.setLevel(PatientAlert.Level.MEDIUM);
            stored.setJsonDetails("{\"synthetic\":true}");

            PatientAlert restored = stored.map();
            assertEquals(supportedClass, restored.getClass());
            assertEquals(stored.id(), restored.id());
            assertEquals(owner, restored.getPatient());
            assertEquals(medicine, restored.getMedicine());
            assertEquals(stored.getType(), restored.getType());
            assertEquals(stored.getLevel(), restored.getLevel());
            assertEquals(stored.getJsonDetails(), restored.getJsonDetails());
        }
    }

    @Test
    public void nullUnknownAndClassLikeStringsAreRejectedBeforeInstantiation() {
        for (String untrusted : new String[]{
                null, "", "java.lang.String", "java.lang.Runtime",
                "es.usc.citius.servando.calendula.persistence.PatientAlert",
                AllergyPatientAlert.class.getCanonicalName() + "$Extra"}) {
            PatientAlert snapshot = new PatientAlert();
            snapshot.setType(untrusted);
            boolean rejected = false;
            try {
                snapshot.map();
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            assertTrue("Only explicit built-in alert types are allowed", rejected);
        }
    }
}
