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

    @Test
    public void selectingDateRejectsEntireSetContainingForeignOrMissingPickup() {
        Patient a = patient(711L);
        Patient b = patient(712L);
        ActiveMedEntity owned = new ActiveMedEntity();
        owned.setId(301L);
        owned.setPatient(a);
        ActiveMedEntity foreign = new ActiveMedEntity();
        foreign.setId(302L);
        foreign.setPatient(b);
        DispensationInfoEntity valid = new DispensationInfoEntity();
        valid.setActiveMed(owned);
        valid.setDispenseInterval(new Interval(
                new DateTime(2026, 10, 26, 0, 0),
                new DateTime(2026, 10, 27, 0, 0)));
        DispensationInfoEntity wrongOwner = new DispensationInfoEntity();
        wrongOwner.setActiveMed(foreign);
        wrongOwner.setDispenseInterval(valid.getDispenseInterval());
        DispensationInfoEntity orphan = new DispensationInfoEntity();
        orphan.setDispenseInterval(valid.getDispenseInterval());

        assertTrue(CalendarActivity.allPickupsBelongToPatient(a,
                java.util.Collections.singletonList(valid)));
        assertFalse(CalendarActivity.allPickupsBelongToPatient(a,
                java.util.Arrays.asList(valid, wrongOwner)));
        assertFalse(CalendarActivity.allPickupsBelongToPatient(a,
                java.util.Arrays.asList(valid, orphan)));
        assertFalse(CalendarActivity.allPickupsBelongToPatient(a,
                java.util.Arrays.asList(valid, null)));
        assertFalse(CalendarActivity.allPickupsBelongToPatient(a, null));
        assertFalse(CalendarActivity.allPickupsBelongToPatient(b,
                java.util.Collections.singletonList(valid)));
    }

    @Test
    public void nextDayPickupLabelUsesProposedDateRatherThanItsPairWrapper() {
        org.joda.time.LocalDate today = new org.joda.time.LocalDate(2026, 10, 27);
        assertTrue(CalendarActivity.isTomorrowPickup(today.plusDays(1), today));
        assertFalse(CalendarActivity.isTomorrowPickup(today, today));
        assertFalse(CalendarActivity.isTomorrowPickup(today.minusDays(1), today));
        assertFalse(CalendarActivity.isTomorrowPickup(today.plusDays(2), today));
        assertFalse(CalendarActivity.isTomorrowPickup(null, today));
        assertFalse(CalendarActivity.isTomorrowPickup(today, null));
    }

    @Test
    public void bestDayInsideIntervalCanStillOfferPickupAdviceWithoutStartDayRows() {
        org.joda.time.LocalDate selected = new org.joda.time.LocalDate(2026, 10, 28);
        androidx.core.util.Pair<org.joda.time.LocalDate,
                java.util.List<DispensationInfoEntity>> best =
                new androidx.core.util.Pair<>(selected,
                        java.util.Collections.singletonList(new DispensationInfoEntity()));
        assertTrue("An eligible day need not be the window start",
                CalendarActivity.isRecommendedPickupDate(selected, best));
        assertFalse(CalendarActivity.isRecommendedPickupDate(selected.minusDays(1), best));
        assertFalse(CalendarActivity.isRecommendedPickupDate(null, best));
        assertFalse(CalendarActivity.isRecommendedPickupDate(selected, null));
        assertFalse(CalendarActivity.isRecommendedPickupDate(selected,
                new androidx.core.util.Pair<>(selected,
                        java.util.Collections.emptyList())));
    }

    private static Patient patient(Long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }
}
