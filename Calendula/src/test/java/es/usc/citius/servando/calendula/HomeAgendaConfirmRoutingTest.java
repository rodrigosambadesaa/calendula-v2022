/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HomeAgendaConfirmRoutingTest {
    private static DailyAgendaItemStub row(long owner, long intakeId, double dose) {
        DailyAgendaItemStub stub =
                new DailyAgendaItemStub(new LocalDate(2026, 10, 14), new LocalTime(12, 0));
        Patient patient = new Patient();
        patient.setId(owner);
        stub.patient = patient;
        DailyAgendaItemStub.DailyAgendaItemStubElement event =
                new DailyAgendaItemStub.DailyAgendaItemStubElement();
        event.eventId = intakeId;
        event.dose = dose;
        stub.meds.add(event);
        return stub;
    }

    @Test
    public void onlyCurrentlyBoundIdentifiedRowCanEnterExplicitConfirmation() {
        DailyAgendaItemStub selected = row(101L, 2001L, 1.5d);
        DailyAgendaItemStub other = row(202L, 3001L, 0.5d);
        assertTrue(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(
                Arrays.asList(selected, other), selected, 0));
        assertFalse(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(
                Arrays.asList(other, selected), selected, 0));
        assertFalse(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(
                Arrays.asList(selected, other), selected, -1));
        assertFalse(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(
                Arrays.asList(selected, other), selected, 2));
        assertFalse(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(
                Collections.emptyList(), selected, 0));
        assertFalse(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(null, selected, 0));
    }

    @Test
    public void malformedVisibleClinicalRowsCannotLaunchBulkConfirmation() {
        assertFalse(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(
                Collections.singletonList(row(101L, 0L, 1d)), row(101L, 0L, 1d), 0));
        for (double bad : new double[]{0d, -1d, Double.NaN, Double.POSITIVE_INFINITY}) {
            DailyAgendaItemStub row = row(101L, 2001L, bad);
            assertFalse(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(
                    Collections.singletonList(row), row, 0));
        }
        DailyAgendaItemStub missingOwner = row(0L, 2001L, 1d);
        assertFalse(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(
                Collections.singletonList(missingOwner), missingOwner, 0));
        DailyAgendaItemStub missingRows = row(101L, 2001L, 1d);
        missingRows.meds.clear();
        assertFalse(DailyAgendaRecyclerAdapter.isCurrentConfirmRow(
                Collections.singletonList(missingRows), missingRows, 0));
    }
}
