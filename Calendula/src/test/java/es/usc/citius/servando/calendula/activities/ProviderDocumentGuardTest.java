/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;

import es.usc.citius.servando.calendula.events.GetExtraInfoEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProviderDocumentGuardTest {
    @Test
    public void onlyTheOriginatingActivePatientCanReceiveProviderDocuments() {
        assertTrue(ProviderDocumentGuard.canAcceptResponse(42L, 42L, 42L));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(42L, 43L, 42L));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(42L, 42L, 43L));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(42L, 42L, null));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(42L, null, 42L));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(null, 42L, 42L));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(0L, 0L, 0L));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(-1L, -1L, -1L));
    }

    @Test
    public void twoConcurrentViewersForSamePatientMustNotReceiveEachOthersResult() {
        assertTrue(ProviderDocumentGuard.canAcceptResponse(42L, 42L, 42L, 15, 15));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(42L, 42L, 42L, 15, 16));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(42L, 42L, 42L, -1, 15));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(42L, 42L, 42L, 15, -1));
        GetExtraInfoEvent first = new GetExtraInfoEvent(
                GetExtraInfoEvent.Status.SUCCESS, "/cache/example.pdf", 42L, 15);
        assertEquals(15, first.getJobId());
        assertTrue(ProviderDocumentGuard.canAcceptResponse(
                42L, 42L, first.getPatientId(), 15, first.getJobId()));
        assertFalse(ProviderDocumentGuard.canAcceptResponse(
                42L, 42L, first.getPatientId(), 16, first.getJobId()));
    }

    @Test
    public void eventPreservesTheOwnerAndLegacyUnknownOwnerFailsClosed() {
        GetExtraInfoEvent scoped =
                new GetExtraInfoEvent(GetExtraInfoEvent.Status.SUCCESS, "/cache/example.pdf", 42L);
        assertEquals(Long.valueOf(42L), scoped.getPatientId());
        assertTrue(ProviderDocumentGuard.canAcceptResponse(
                42L, 42L, scoped.getPatientId()));
        GetExtraInfoEvent legacy =
                new GetExtraInfoEvent(GetExtraInfoEvent.Status.SUCCESS, "/cache/legacy.pdf");
        assertNull(legacy.getPatientId());
        assertFalse(ProviderDocumentGuard.canAcceptResponse(
                42L, 42L, legacy.getPatientId()));
    }
}
