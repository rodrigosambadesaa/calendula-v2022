package es.usc.citius.servando.calendula.healthcareprovider.util

import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime
import org.joda.time.LocalTime
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prescribed time differences must produce a missing-intake comparison,
 * never abort medication schedule review with NoSuchElementException.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScheduleComparatorTimeLookupTest {
    @Test
    fun missingDoseTimeReturnsNullWithoutThrowing() {
        val nine = DailyFixedTime(
            DailyFixedTime.ReferenceType.ABSOLUTE, LocalTime(9, 0))
        assertNull(ScheduleComparator.matchingScheduledTime(
            listOf(nine), LocalTime(10, 30)))
        assertNull(ScheduleComparator.matchingScheduledTime(
            emptyList(), LocalTime(10, 30)))
    }

    @Test
    fun exactDoseTimeIsMatched() {
        val nine = DailyFixedTime(
            DailyFixedTime.ReferenceType.ABSOLUTE, LocalTime(9, 0))
        val ten = DailyFixedTime(
            DailyFixedTime.ReferenceType.ABSOLUTE, LocalTime(10, 30))
        assertSame(ten, ScheduleComparator.matchingScheduledTime(
            listOf(nine, ten), LocalTime(10, 30)))
    }
}
