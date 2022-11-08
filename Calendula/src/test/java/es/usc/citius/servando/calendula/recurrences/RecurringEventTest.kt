/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.recurrences

import android.util.Pair
import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent.WeekDay
import es.usc.citius.servando.calendula.util.LogUtil
import org.dmfs.rfc5545.recur.Freq
import org.joda.time.*
import org.joda.time.format.DateTimeFormat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(constants = BuildConfig::class)
class RecurringEventTest {

    @Test
    @Throws(Exception::class)
    fun testRoutine() {

        val start = DateTime.parse("2017/06/01 08:00", dtf)
        val end = DateTime.parse("2017/08/31 08:00", dtf)
        val interval = Interval(start, end)

        val breakfast = LocalTime.parse("09:00")
        val lunch = LocalTime.parse("14:00")
        val dinner = LocalTime.parse("21:30")

        val recur = RecurringEvent.Builder()
            .repeatDaily()
            .from(start)
            .to(end)
            .atFixedTime(breakfast)
            .atFixedTime(lunch)
            .atFixedTime(dinner)
            .build()

        // generate an interval from start -5 days to end +10 days
        val range = Interval(start.minusDays(5), end.plusDays(10))
        // get the event occurrences in the interval
        val events = recur.occurrencesIn(range.start, range.end)
        val overlap = interval.overlap(range)

        // we should expect 3 events every day in the overlap
        val expectedEvents = overlap.toDuration().standardDays * 3
        assertEquals(expectedEvents, events.size.toLong())

        var expectedDate: LocalDate
        // check every generated event dateTime
        var i = 0
        while (i < events.size) {

            expectedDate = start.plusDays(i / 3).toLocalDate()

            val a = events[i].time()
            val b = events[i + 1].time()
            val c = events[i + 2].time()

            assertEquals(expectedDate.toDateTime(breakfast), a)
            assertEquals(expectedDate.toDateTime(lunch), b)
            assertEquals(expectedDate.toDateTime(dinner), c)
            i += 3
        }
    }

    @Test
    @Throws(Exception::class)
    fun testHourly() {

        val start = DateTime.parse("2017/06/01 10:00", dtf)
        val end = start.plusDays(20)
        val interval = Interval(start, end)

        val recur = RecurringEvent.Builder()
            .repeatEvery(12, Freq.HOURLY)
            .from(start)
            .to(end)
            .build()

        var range: Interval
        var expectedEvents: Long
        val ranges = ArrayList<Pair<Int, Int>>()
        ranges.add(Pair(-6, -7)) // from before start to after end
        ranges.add(Pair(-3, +8)) // from before start to before end
        ranges.add(Pair(+2, -4)) // from after start to before end
        ranges.add(Pair(+5, +9)) // from after start to after end

        for (r in ranges) {
            range = Interval(start.plusDays(r.first), end.plusDays(r.second))
            expectedEvents = range.overlap(interval).toDuration().standardDays * 2
            val events = recur.occurrencesIn(range.start, range.end)
            assertEquals(expectedEvents, events.size.toLong())
            // check all the events
            var expected = if (r.first > 0) start.plusDays(r.first) else start
            for (eti in events) {
                assertEquals(expected, eti.time())
                expected = expected.plusHours(12)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDaily() {

        val start = DateTime.parse("2017/06/01 10:00", dtf)
        val end = start.plusDays(3 * 10) // 10 events

        val recur = RecurringEvent.Builder()
            .repeatEvery(3, Freq.DAILY)
            .from(start)
            .to(end)
            .build()

        val range = Interval(start.minusDays(10), end.plusDays(10))
        val events = recur.occurrencesIn(range.start, range.end)
        assertEquals(10, events.size.toLong())
        // check all the events
        var expected = start
        for (timeInfo in events) {
            assertEquals(expected, timeInfo.time())
            expected = expected.plusDays(3)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testWeekly() {

        val start = DateTime.parse("2017/05/29 00:00", dtf) // MON
        val end = DateTime.parse("2017/06/30 00:00", dtf)

        val recur = RecurringEvent.Builder()
            .repeatEvery(2, Freq.WEEKLY)
            .repeatWeekdays(WeekDay.WE, WeekDay.TH)
            .from(start)
            .to(end)
            .build()


        LogUtil.d(TAG, "Rule: " + recur.recurrenceRule())
        val events = recur.occurrencesIn(start, end)
        val expectedEvents: Long = 6 // every two weeks for 20 weeks, 2 times a week
        assertEquals(expectedEvents, events.size.toLong())
        // check all the events
        var expected = start.plusDays(2)
        for (i in events.indices) {
            val dateTime = events[i].time()
            LogUtil.d(TAG, dateTime.toString(dtf) + ", dayOfWeek: " + dateTime.dayOfWeek)
            assertEquals(expected, dateTime)
            expected =
                    expected.plusDays(if (dateTime.dayOfWeek == DateTimeConstants.WEDNESDAY) 1 else 13)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testCycle() {

        val activeDays = 10
        val inactiveDays = 5
        val numberOfCycles = 10
        val start = DateTime.parse("2017/06/01 08:00", dtf)
        val fixedTime = LocalTime.parse("22:00")

        val recur = RecurringEvent.Builder()
            .repeatCyclic(activeDays, inactiveDays)
            .from(start)
            .atFixedTime(fixedTime)
            .build()

        var rangeStart = start.minusDays(8)
        var rangeEnd = start.plusDays((activeDays + inactiveDays) * numberOfCycles)
        var events: List<RecurringEvent.EventTimeInfo> = recur.occurrencesIn(rangeStart, rangeEnd)
        assertEquals((numberOfCycles * activeDays).toLong(), events.size.toLong())
        assertEquals(fixedTime.toDateTime(start), events[0].time())
        assertEquals(
            fixedTime.toDateTime(rangeEnd.minusDays(inactiveDays).minusDays(1)),
            events[events.size - 1].time()
        )

        rangeStart = start.plusDays(8)
        rangeEnd = start.plusDays((activeDays + inactiveDays) * numberOfCycles)
        events = recur.occurrencesIn(rangeStart, rangeEnd)
        assertEquals((numberOfCycles * activeDays - 8).toLong(), events.size.toLong())
        assertEquals(fixedTime.toDateTime(rangeStart), events[0].time())
        assertEquals(
            fixedTime.toDateTime(rangeEnd.minusDays(inactiveDays).minusDays(1)),
            events[events.size - 1].time()
        )
    }


    @Test
    @Throws(Exception::class)
    fun testConsistency() {

        try {
            RecurringEvent.Builder()
                .repeatCyclic(10, null)
                .from(DateTime.now())
                .build()

            // Build should throw an exception
            fail("Inactive days can not be null!")

        } catch (e: RecurringEvent.InvalidRecurringEventException) {
            LogUtil.d(TAG, "Exception thrown: " + e.message)
            if (!e.message!!.contains("Inconsistent event")) {
                throw RuntimeException("Unexpected exception", e)
            }
        }

        try {
            RecurringEvent.Builder()
                .repeatEvery(5, Freq.DAILY)
                .build()

            // Build should throw an exception
            fail("Rules with interval must have an start!")

        } catch (e: RecurringEvent.InvalidRecurringEventException) {
            LogUtil.d(TAG, "Exception thrown: " + e.message)
            if (!e.message!!.contains("Inconsistent event")) {
                throw RuntimeException("Unexpected exception", e)
            }
        }

    }


    @Test
    @Throws(Exception::class)
    fun testHasOccurrences() {
        hasOccurrences(LocalDate.parse("2017/06/01", df)) //TH
        hasOccurrences(LocalDate.parse("2017/06/05", df)) //MO
    }

    @Test
    @Throws(Exception::class)
    fun testHasOccurrencesWrongStart() {
        hasOccurrences(LocalDate.parse("2017/05/31", df)) //WED
        hasOccurrences(LocalDate.parse("2017/05/03", df)) // SAT
    }

    @Test
    @Throws(Exception::class)
    fun testGetDays() {
        val event = RecurringEvent.Builder()
            .repeatWeekdays(WeekDay.MO, WeekDay.TH)
            .from(DateTime.now())
            .build()

        val expected = BooleanArray(7)
        expected[0] = true
        expected[3] = true
        assertEquals(Arrays.toString(expected), Arrays.toString(event.byDay()))
    }

    @Throws(Exception::class)
    private fun hasOccurrences(start: LocalDate) {

        val week = DateTimeFormat.forPattern("EEE")
        val end = start.plusMonths(3)
        val event = RecurringEvent.Builder()
            .repeatDaily()
            .repeatWeekdays(WeekDay.MO, WeekDay.TH)
            .from(start)
            .to(end)
            .build()

        var from = start//.minusDays(5);
        val to = end.plusDays(5)

        while (from.isBefore(to)) {
            val has = event.hasOccurrencesAt(from)
            LogUtil.d(
                TAG,
                from.toString(df) + ", dayOfWeek: " + from.toString(week) + ", has: " + has
            )
            // between start and end, if MO or TH
            if (!from.isBefore(start) && from.isBefore(end) && (from.dayOfWeek == DateTimeConstants.MONDAY || from.dayOfWeek == DateTimeConstants.THURSDAY)) {
                assertEquals(true, has)
            } else {
                assertEquals(false, has)
            }
            from = from.plusDays(1)
        }
    }

    companion object {

        const val TAG = "RecurringEventTest"
        val dtf = DateTimeFormat.forPattern("YYYY/MM/dd HH:mm")!!
        val df = DateTimeFormat.forPattern("YYYY/MM/dd")!!
    }
}