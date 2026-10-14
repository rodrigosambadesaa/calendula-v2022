/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence.alerts;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Clinical warnings may not show only a known subset as a complete list. */
public class AllergyAlertPresentationGuardTest {
    @Test
    public void acceptsCompleteNames() {
        assertTrue(AllergyAlertPresentationGuard.hasCompleteNames(
                Collections.singletonList("Peanut")));
        assertTrue(AllergyAlertPresentationGuard.hasCompleteNames(
                Arrays.asList("Peanut", "Lactose")));
    }

    @Test
    public void rejectsMissingAndPartialLegacyDetails() {
        assertFalse(AllergyAlertPresentationGuard.hasCompleteNames(null));
        assertFalse(AllergyAlertPresentationGuard.hasCompleteNames(
                Collections.emptyList()));
        assertFalse(AllergyAlertPresentationGuard.hasCompleteNames(
                Arrays.asList("Peanut", null)));
        assertFalse(AllergyAlertPresentationGuard.hasCompleteNames(
                Arrays.asList("Peanut", "   ")));
        assertFalse(AllergyAlertPresentationGuard.hasCompleteNames(
                Collections.singletonList("")));
    }
}
