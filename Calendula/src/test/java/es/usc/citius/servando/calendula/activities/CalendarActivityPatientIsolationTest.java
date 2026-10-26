package es.usc.citius.servando.calendula.activities;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import static org.junit.Assert.*;

/** Synthetic owner identifiers only; no medication or provider data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CalendarActivityPatientIsolationTest {
    @Test
    public void pickupCalendarModelMustNotBeGlobalAcrossPatients() throws Exception {
        Field storage = CalendarActivity.class.getDeclaredField("dispensationInfoStore");
        assertFalse("A pickup calendar belongs to one activity, not all users",
                Modifier.isStatic(storage.getModifiers()));
    }

    @Test
    public void onlyOriginalPersistedPatientMayReceiveBackgroundPickupResults() {
        Patient requested = patient(1048577L);
        Patient samePersistedRow = patient(Long.valueOf("1048577"));
        Patient another = patient(1048578L);
        assertTrue(CalendarActivity.samePersistedPatient(requested, samePersistedRow));
        assertFalse(CalendarActivity.samePersistedPatient(requested, another));
        assertFalse(CalendarActivity.samePersistedPatient(requested, null));
        assertFalse(CalendarActivity.samePersistedPatient(null, requested));
        assertFalse(CalendarActivity.samePersistedPatient(patient(0L), patient(0L)));
        assertFalse(CalendarActivity.samePersistedPatient(new Patient(), new Patient()));
    }

    @Test
    public void pickupMustLinkToTheExactProviderRowAndPatientWithAnInterval() {
        Patient a = patient(101L);
        Patient b = patient(102L);
        ActiveMedEntity providerA = new ActiveMedEntity();
        providerA.setId(500L);
        providerA.setPatient(a);
        ActiveMedEntity providerB = new ActiveMedEntity();
        providerB.setId(501L);
        providerB.setPatient(b);
        DispensationInfoEntity pickup = new DispensationInfoEntity();
        pickup.setActiveMed(providerA);
        pickup.setDispenseInterval(new Interval(DateTime.now(), DateTime.now().plusDays(1)));

        assertTrue(CalendarActivity.isOwnedPickup(a, providerA, pickup));
        assertFalse(CalendarActivity.isOwnedPickup(b, providerA, pickup));
        assertFalse(CalendarActivity.isOwnedPickup(a, providerB, pickup));
        assertFalse(CalendarActivity.isOwnedPickup(null, providerA, pickup));
        assertFalse(CalendarActivity.isOwnedPickup(a, providerA, null));

        pickup.setActiveMed(providerB);
        assertFalse(CalendarActivity.isOwnedPickup(a, providerA, pickup));
        pickup.setActiveMed(providerA);
        pickup.setDispenseInterval(null);
        assertFalse(CalendarActivity.isOwnedPickup(a, providerA, pickup));
    }

    private static Patient patient(Long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }
}
