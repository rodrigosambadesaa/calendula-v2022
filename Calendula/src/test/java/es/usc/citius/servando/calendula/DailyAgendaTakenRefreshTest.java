/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/** Synthetic medication-completion state; no real patient records. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DailyAgendaTakenRefreshTest {

    private Patient patient(long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private EventInstance event(long eventId, Patient p, boolean completed) {
        EventInstance e = new EventInstance(DateTime.now(), EventType.MEDICATION_INTAKE);
        e.setId(Long.valueOf(eventId));
        e.setPatient(p);
        e.setCompleted(completed);
        return e;
    }

    @Test
    public void separatelyBoxedLongIdsStillUpdateTheTakenIndicator() {
        Patient displayPatient = patient(901001L);
        DailyAgendaItemStub stub = new DailyAgendaItemStub(
                DateTime.now().toLocalDate(), DateTime.now().toLocalTime());
        stub.patient = displayPatient;
        DailyAgendaItemStub.DailyAgendaItemStubElement matching =
                new DailyAgendaItemStub.DailyAgendaItemStubElement();
        DailyAgendaItemStub.DailyAgendaItemStubElement other =
                new DailyAgendaItemStub.DailyAgendaItemStubElement();
        matching.eventId = Long.valueOf(2000000L);
        other.eventId = Long.valueOf(2000001L);
        stub.meds.add(matching);
        stub.meds.add(other);
        EventInstance confirmed = event(2000000L, patient(901001L), true);
        assertNotSame("Fixture must reproduce distinct boxed Long references",
                matching.eventId, confirmed.getId());

        DailyAgendaRecyclerAdapter.refreshTakenStatus(
                stub, Collections.singletonList(confirmed));

        assertTrue("Confirming the same persisted event must update the UI",
                matching.taken);
        assertFalse("An unrelated medicine's status must remain unchanged",
                other.taken);
    }

    @Test
    public void cannotUpdateAnotherPatientsOrUnidentifiedDose() {
        Patient owner = patient(901001L);
        DailyAgendaItemStub stub = new DailyAgendaItemStub(
                DateTime.now().toLocalDate(), DateTime.now().toLocalTime());
        stub.patient = owner;
        DailyAgendaItemStub.DailyAgendaItemStubElement current =
                new DailyAgendaItemStub.DailyAgendaItemStubElement();
        current.eventId = 2000000L;
        stub.meds.add(current);
        EventInstance wrongPatient = event(2000000L, patient(901002L), true);
        EventInstance missingOwner = event(2000000L, null, true);
        EventInstance missingId = event(2000000L, owner, true);
        missingId.setId(null);

        DailyAgendaRecyclerAdapter.refreshTakenStatus(stub,
                Arrays.asList(wrongPatient, missingOwner, missingId, null));
        assertFalse("Cross-patient and unidentified doses cannot change UI state",
                current.taken);
    }

    @Test
    public void noActivePatientOrEmptyEventListIsSafe() {
        DailyAgendaItemStub stub = new DailyAgendaItemStub(
                DateTime.now().toLocalDate(), DateTime.now().toLocalTime());
        DailyAgendaItemStub.DailyAgendaItemStubElement item =
                new DailyAgendaItemStub.DailyAgendaItemStubElement();
        item.eventId = 2000000L;
        stub.meds.add(item);
        DailyAgendaRecyclerAdapter.refreshTakenStatus(
                stub, Collections.singletonList(event(2000000L, patient(901001L), true)));
        assertFalse(item.taken);
        stub.patient = patient(901001L);
        DailyAgendaRecyclerAdapter.refreshTakenStatus(stub, null);
        assertFalse(item.taken);
    }
}
