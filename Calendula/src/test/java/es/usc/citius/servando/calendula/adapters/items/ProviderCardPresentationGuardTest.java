/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.adapters.items;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure tests for malformed external dosage and absent update timestamps. */
public class ProviderCardPresentationGuardTest {
    @Test
    public void missingDosageMustNotReadEntryZero() {
        assertFalse(ProviderCardPresentationGuard.hasFirstDosageEntry(-1));
        assertFalse(ProviderCardPresentationGuard.hasFirstDosageEntry(0));
        assertTrue(ProviderCardPresentationGuard.hasFirstDosageEntry(1));
    }

    @Test
    public void malformedProviderRepeatPeriodsMustNeverAppearAsValidSchedule() {
        org.hl7.fhir.dstu3.model.Timing.UnitsOfTime hours =
                org.hl7.fhir.dstu3.model.Timing.UnitsOfTime.H;
        for (Double invalid : new Double[]{null, -1.0d, 0.0d,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(ProviderCardPresentationGuard.canRenderPeriod(invalid, hours));
        }
        assertFalse(ProviderCardPresentationGuard.canRenderPeriod(8.0d, null));
        assertTrue(ProviderCardPresentationGuard.canRenderPeriod(0.5d, hours));
        assertTrue(ProviderCardPresentationGuard.canRenderPeriod(8.0d, hours));
        assertTrue(ProviderCardPresentationGuard.canRenderPeriod(
                1.0d, org.hl7.fhir.dstu3.model.Timing.UnitsOfTime.D));
    }

    @Test
    public void missingFutureOrInvalidUpdateTimestampsMustNotBeDisplayed() {
        long now = 1_000_000_000L;
        assertFalse(ProviderCardPresentationGuard.mayShowUpdateDate(null, now, false));
        assertFalse(ProviderCardPresentationGuard.mayShowUpdateDate(-1L, now, false));
        assertFalse(ProviderCardPresentationGuard.mayShowUpdateDate(now + 1000L, now, false));
        assertTrue(ProviderCardPresentationGuard.mayShowUpdateDate(now - 3000L, now, true));
        assertTrue(ProviderCardPresentationGuard.mayShowUpdateDate(now - 4L * 86400000L, now, false));
        assertFalse(ProviderCardPresentationGuard.mayShowUpdateDate(now - 4L * 86400000L, now, true));
    }
}
