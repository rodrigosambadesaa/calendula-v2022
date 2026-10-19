package es.usc.citius.servando.calendula.healthcareprovider.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic patient identifiers only; no SQLite or clinical data. */
class ScheduleComparatorOwnershipTest {

    @Test
    fun validOwnerAndMissingOptionalLocalMedicineMayBeCompared() {
        assertTrue(ScheduleComparator.ownersMatchForComparison(
            101L, 101L, true, 101L))
        assertTrue(ScheduleComparator.ownersMatchForComparison(
            101L, 101L, false, null))
    }

    @Test
    fun crossPatientOrUnpersistedLinksAreRejected() {
        assertFalse(ScheduleComparator.ownersMatchForComparison(
            101L, 102L, true, 101L))
        assertFalse(ScheduleComparator.ownersMatchForComparison(
            101L, 101L, true, 102L))
        assertFalse(ScheduleComparator.ownersMatchForComparison(
            101L, 101L, true, null))
        assertFalse(ScheduleComparator.ownersMatchForComparison(
            null, 101L, true, 101L))
        assertFalse(ScheduleComparator.ownersMatchForComparison(
            0L, 0L, false, null))
        assertFalse(ScheduleComparator.ownersMatchForComparison(
            101L, null, false, null))
    }
}
