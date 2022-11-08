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
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.persistence.Routine
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent
import es.usc.citius.servando.calendula.util.GsonUtil
import es.usc.citius.servando.calendula.util.LogUtil
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog


@RunWith(RobolectricTestRunner::class)
@Config(constants = BuildConfig::class)
class RecurringEventPersistenceTest {

    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestUtils.resetDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testPersistence() {

        val r = Routine()
        r.name = "Lunch"
        r.patient = DB.patients().default
        r.time = LocalTime.parse("13:00")

        DB.routines().save(r)


        val start = LocalDate.now()
        val end = start.plusMonths(5)
        val event = RecurringEvent.Builder()
            .repeatDaily()
            .atFixedTime(LocalTime.parse("08:00"))
            .atFixedTime(DailyFixedTime(r.id, DailyFixedTime.ReferenceType.ROUTINE))
            .from(start)
            .to(end)
            .build()

        val json = GsonUtil.get().toJson(event)

        LogUtil.d(TAG, "JSON: " + json)

        val copy = GsonUtil.get().fromJson(json, RecurringEvent::class.java)

        assertEquals(json, GsonUtil.get().toJson(copy))


    }

    companion object {

        const val TAG = "RecurringEventTest"
    }
}