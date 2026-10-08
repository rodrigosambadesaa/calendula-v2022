/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.persistence.Medicine;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.nullable;

/** Pure offline checks, without SQLite writes or patient data. */
public class RetainMedicineCodeOnCatalogMismatchTest {

    @Test
    public void unmatchedCatalogPresentationLeavesOriginalCodeUntouched() {
        Medicine missing = mock(Medicine.class);
        when(missing.isBoundToPrescription()).thenReturn(true);
        when(missing.getCn()).thenReturn("123456");
        Medicine matched = mock(Medicine.class);
        when(matched.isBoundToPrescription()).thenReturn(true);
        when(matched.getCn()).thenReturn("654321");
        Prescription known = mock(Prescription.class);

        int count = InstallDatabaseService.countUnresolvedLinks(
                Arrays.asList(missing, matched),
                cn -> "654321".equals(cn) ? known : null,
                () -> false);

        assertEquals(1, count);
        verify(missing).getCn();
        verify(matched).getCn();
        verify(missing, never()).setCn(nullable(String.class));
        verify(matched, never()).setCn(nullable(String.class));
    }

    @Test
    public void unlinkedMedicineIsNotMistakenForADeletedCatalogRecord() {
        Medicine local = mock(Medicine.class);
        when(local.isBoundToPrescription()).thenReturn(false);

        assertEquals(0, InstallDatabaseService.countUnresolvedLinks(
                Collections.singletonList(local),
                cn -> { throw new AssertionError("Unlinked medicine must not be looked up"); },
                () -> false));
        verify(local, never()).setCn(nullable(String.class));
    }

    @Test
    public void interruptionStopsScanWithoutPublishingPartialCount() {
        Medicine item = mock(Medicine.class);
        when(item.isBoundToPrescription()).thenReturn(true);
        when(item.getCn()).thenReturn("123456");
        AtomicInteger calls = new AtomicInteger();
        assertEquals(-1, InstallDatabaseService.countUnresolvedLinks(
                Arrays.asList(item, item), cn -> null,
                () -> calls.incrementAndGet() > 1));
        verify(item, never()).setCn(nullable(String.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullMedicineRowsMustNotProduceValidReconciliationResult() {
        InstallDatabaseService.countUnresolvedLinks(
                Collections.singletonList(null), cn -> null, () -> false);
    }
}
