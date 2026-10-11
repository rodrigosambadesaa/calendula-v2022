package es.usc.citius.servando.calendula.healthcareprovider.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for the historical receivedName != receivedName comparison:
 * a server-side renaming must trigger review rather than silently disappear.
 * Uses fabricated strings; no real patient, prescription or token data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class ActiveMedDisplayNameTest {
    @Test
    fun changedRemoteMedicationNameRequiresReview() {
        assertTrue(ActiveMedUpdater.displayNameChanged(
            "Synthetic new title", "Synthetic old title"))
        assertTrue(ActiveMedUpdater.displayNameChanged(
            "Synthetic new title", null))
        assertTrue(ActiveMedUpdater.displayNameChanged(
            null, "Synthetic old title"))
    }

    @Test
    fun unchangedNameDoesNotCauseRepeatedReview() {
        assertFalse(ActiveMedUpdater.displayNameChanged(
            "Same synthetic title", "Same synthetic title"))
        assertFalse(ActiveMedUpdater.displayNameChanged(null, null))
    }
}
