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

import android.content.Context
import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.TestUtils
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.persistence.Schedule
import es.usc.citius.servando.calendula.scheduling.Agenda
import es.usc.citius.servando.calendula.scheduling.model.EventInstance
import es.usc.citius.servando.calendula.scheduling.model.EventReminder
import es.usc.citius.servando.calendula.scheduling.model.EventReminderReceiver
import es.usc.citius.servando.calendula.scheduling.model.EventType
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent
import es.usc.citius.servando.calendula.util.LogUtil
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class AgendaTest {

    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestUtils.resetDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testAgenda() {

        val today = LocalDate.parse("2017/06/01", dtf)
        val dinner = LocalTime.parse("21:00")
        val spacer = "----------------------------"

        val m1 = Medicine("Ibuprofen")
        val m2 = Medicine("Paracetamol")
        // db mock
        val schedules = HashMap<Long, Schedule>()

        // first of all, register a reminder to handle reminders
        // this should be done on every application startup
        Agenda.instance()
            .registerReminderReceiver(EventType.MEDICATION_INTAKE, object : EventReminderReceiver {
                override fun onEvent(c: Context, e: EventReminder) {
                    // reminder arrived!
                    val type = e.eventType
                    val dateTime = e.dateTime
                    // get all the medication events that should be
                    // reminded to the user from the database
                    val events = Agenda.instance().getEvents(type, dateTime)

                    if (dateTime.toLocalTime() == dinner) {
                        assertEquals(2, events.size.toLong())
                    } else {
                        assertEquals(1, events.size.toLong())
                    }

                    // Build a notification
                    val builder = StringBuilder(spacer).append("\n")
                    builder.append("\t * Time: " + dateTime.toString(f)).append("\n")
                    builder.append("\t * Medicines: " + dateTime.toString(f)).append("\n")
                    for (evt in events) {
                        val scheduleId = evt.ref
                        val s = schedules[scheduleId]
                        val med = s!!.medicine.name
                        val dosage = evt.getDoubleParam(EventInstance.PARAM_DOSE)
                        builder.append("\t\t  - $med: $dosage").append("\n")
                    }
                    LogUtil.d(TAG, builder.append(spacer).toString())
                }

                override fun autoRepeat(): Boolean {
                    return false
                }
            })


        val events = ArrayList<EventInstance>()


        // create 2 schedules
        val s1 = Schedule(
            RecurringEvent.Builder()
                .repeatDaily()
                .from(today)
                .atFixedTime(dinner)
                .build()
        )

        s1.medicine = m1
        s1.setDosages(Arrays.asList(2.0))
        s1.id = 1L

        val s2 = Schedule(
            RecurringEvent.Builder()
                .repeatDaily()
                .from(today)
                .atFixedTime(LocalTime.parse("08:00"))
                .build()
        )

        s2.recur.edit()
            .clearDailyFixedTimes()
            .atFixedTime(dinner)
            .atFixedTime(dinner.minusHours(5))
            .commit()

        s2.medicine = m2
        s2.setDosages(Arrays.asList(1.0, 1.0))
        s2.id = 2L

        // save schedules (mock Dao.save())
        schedules[s1.id] = s1
        schedules[s2.id] = s2

        // get all the events generated by the 2 schedules for the next 3 days
        events.addAll(
            s1.getEventsBetween(
                today.toDateTimeAtStartOfDay(),
                today.plusDays(3).toDateTimeAtStartOfDay()
            )
        )
        events.addAll(
            s2.getEventsBetween(
                today.toDateTimeAtStartOfDay(),
                today.plusDays(3).toDateTimeAtStartOfDay()
            )
        )

        LogUtil.d(TAG, "Events: " + events.size)

        assertEquals(((1 + 2) * 3).toLong(), events.size.toLong())

        // Save the events to the database
        Agenda.instance().addEvents(events)
        // create the reminders
        //        Agenda.instance().createReminders(events);
        //
        //        // Fire reminders
        //        Agenda.AlarmManagerMock.fireAlarms();

    }

    companion object {

        const val TAG = "AgendaTest"

        val dtf = DateTimeFormat.forPattern("yyyy/MM/dd")!!
        val f = DateTimeFormat.forPattern("YYYY/MM/dd HH:mm")!!
    }

}