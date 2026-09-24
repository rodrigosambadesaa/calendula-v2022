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

import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.TestUtils
import es.usc.citius.servando.calendula.util.LogUtil
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class RecurrenceRuleTest {

    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestUtils.resetDatabase()
    }


    @Test
    @Throws(Exception::class)
    fun testConvert() {

        val dateTime = DateTime.parse("2017/05/01 20:48", f)
        assertEquals(dateTime, convert(convert(dateTime)))
    }


    @Test
    @Throws(Exception::class)
    fun testDailyRecurrence() {

        val rule = RecurrenceRule("FREQ=DAILY", mode)
        val start = DateTime.parse("2017/01/01 08:00", f)
        val end = DateTime.parse("2017/01/30 23:00", f)

        LogUtil.d(TAG, start.toString())
        LogUtil.d(TAG, convert(start).toString())

        val it = rule.iterator(convert(start))

        var maxInstances = 100 // limit instances for rules that recur forever
        var expected = DateTime(start)
        while (it.hasNext() && (!rule.isInfinite || maxInstances-- > 0)) {
            val next = convert(it.nextDateTime())
            assertEquals(expected, next)
            expected = expected.plusDays(1)

            LogUtil.d(TAG, "Next instance : " + next.toString(f))

            if (next.isAfter(end)) {
                break
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDailyRecurrence2() {

        val rule = RecurrenceRule("FREQ=DAILY;INTERVAL=3;", mode)
        val start = DateTime.parse("2017/01/01", dtf)
        val end = DateTime.parse("2017/01/30", dtf)

        LogUtil.d(TAG, start.toString())
        LogUtil.d(TAG, convert(start).toString())

        val it = rule.iterator(convert(start))

        var maxInstances = 100 // limit instances for rules that recur forever
        var expected = DateTime(start)
        while (it.hasNext() && (!rule.isInfinite || maxInstances-- > 0)) {
            val next = convert(it.nextDateTime())
            assertEquals(expected, next)
            expected = expected.plusDays(3)

            LogUtil.d(TAG, "Next instance : " + next.toString(f))

            if (next.isAfter(end)) {
                break
            }
        }
    }


    @Test
    @Throws(Exception::class)
    fun testByDayRecurrence() {

        val rule = RecurrenceRule("FREQ=DAILY;BYDAY=TU,SU;", mode)
        val start = DateTime.parse("2017/01/01", dtf)
        val end = DateTime.parse("2017/01/30", dtf)

        LogUtil.d(TAG, start.toString())
        LogUtil.d(TAG, convert(start).toString())

        val it = rule.iterator(convert(start))

        var maxInstances = 100 // limit instances for rules that recur forever
        var expected = DateTime.parse("2017/01/02", dtf) // first tuesday
        while (it.hasNext() && (!rule.isInfinite || maxInstances-- > 0)) {
            val next = convert(it.nextDateTime())
            LogUtil.d(TAG, "Next instance : " + next.toString(f) + ", " + expected.toString(f))

            //assertEquals(expected, next);
            // go from tuesday to sunday
            expected = expected.plusDays(if (expected.dayOfWeek == 2) 5 else 2)

            if (next.isAfter(end)) {
                break
            }
        }
    }


    @Test
    @Throws(Exception::class)
    fun testHourlyRecurrence() {
        val rule = RecurrenceRule("FREQ=HOURLY;INTERVAL=8;", mode)
        val start = DateTime.parse("2017/01/01", dtf)
        val end = DateTime.parse("2017/01/30", dtf)
        val it = rule.iterator(convert(start))

        var maxInstances = 100 // limit instances for rules that recur forever

        var expected = DateTime(start)
        while (it.hasNext() && (!rule.isInfinite || maxInstances-- > 0)) {
            val next = convert(it.nextDateTime())
            assertEquals(expected, next)
            expected = expected.plusHours(8)
            LogUtil.d(TAG, "Next instance : " + next.toString(f))

            if (next.isAfter(end)) {
                break
            }
        }
    }


    @Test
    @Throws(Exception::class)
    fun testPeriodRestRecurrence() {
        val rule = RecurrenceRule("FREQ=DAILY;INTERVAL=30;COUNT=10", mode)
        val start = DateTime.parse("2017/01/01", dtf)
        val end = DateTime.parse("2017/12/31", dtf)
        val it = rule.iterator(convert(start))

        var maxInstances = 100 // limit instances for rules that recur forever

        var expected = DateTime(start)
        while (it.hasNext() && (!rule.isInfinite || maxInstances-- > 0)) {
            val next = convert(it.nextDateTime())
            //assertEquals(expected, next);
            expected = expected.plusHours(8)
            LogUtil.d(TAG, "PeriodRest : " + next.toString(f))

            if (next.isAfter(end)) {
                break
            }
        }
    }


    // Convert between JodaTime and rfc5545 DateTime

    private fun convert(dt: DateTime): org.dmfs.rfc5545.DateTime {
        return org.dmfs.rfc5545.DateTime(
            // date
            dt.year, dt.monthOfYear - 1, dt.dayOfMonth,
            // time
            dt.hourOfDay, dt.minuteOfHour, dt.secondOfMinute
        )
    }

    private fun convert(dt: org.dmfs.rfc5545.DateTime): DateTime {
        return DateTime(
            // date
            dt.year, dt.month + 1, dt.dayOfMonth,
            // time
            dt.hours, dt.minutes, dt.seconds
        )
    }

    companion object {

        const val TAG = "RecurrenceRuleTest"

        val mode: RecurrenceRule.RfcMode = RecurrenceRule.RfcMode.RFC5545_STRICT
        val dtf = DateTimeFormat.forPattern("yyyy/MM/dd")!!
        val f = DateTimeFormat.forPattern("YYYY/MM/dd HH:mm")!!
    }

}