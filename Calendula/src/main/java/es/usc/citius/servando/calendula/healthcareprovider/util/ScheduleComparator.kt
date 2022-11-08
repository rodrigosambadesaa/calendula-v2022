/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
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

package es.usc.citius.servando.calendula.healthcareprovider.util

import android.content.Context
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.persistence.Schedule
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType
import org.dmfs.rfc5545.recur.Freq
import org.joda.time.DateTime
import org.joda.time.LocalTime

object ScheduleComparator {

    fun compare(activeMed: ActiveMedVO, schedule: Schedule): Collection<Change> {

        val changes = ArrayList<Change>()
        changes.addAll(checkActiveMed(activeMed, schedule))
        changes.addAll(checkStartAndEnd(activeMed, schedule))
        val dosageChanges = checkDosageCount(activeMed, schedule)
        changes.addAll(dosageChanges)
        // add dosage entry changes if there are no dosage count
        // related changes that might be causing more differences
        if (dosageChanges.isEmpty()) {
            changes.addAll(checkDosageEntries(activeMed, schedule))
        }
        return changes
    }


    private fun checkStartAndEnd(activeMed: ActiveMedVO, schedule: Schedule): Collection<Change> {
        val changes = ArrayList<Change>()

        activeMed.validityStart?.let {
            if (it.toLocalDate() != schedule.recur.startDateTime?.toLocalDate()) {
                changes.add(StartDateChange(it, schedule.recur.startDateTime))
            }
        }
        activeMed.validityEnd?.let {
            if (it.toLocalDate() != schedule.recur.endDateTime?.toLocalDate()) {
                changes.add(EndDateChange(it, schedule.recur.endDateTime))
            }
        }

        return changes
    }

    private fun checkActiveMed(activeMed: ActiveMedVO, schedule: Schedule): Collection<Change> {
        val changes = ArrayList<Change>()
        val entity =
            DB.healthcareProviderDB().activeMeds().findOneBy(ActiveMedEntity.COLUMN_CODE, activeMed.code)

        if (schedule.medicine == null) {
            changes.add(ActiveMedChange("", activeMed.display))
        } else if (!schedule.medicine.isFromActiveMed || entity.id != schedule.medicine.activeMedId) {
            changes.add(ActiveMedChange(schedule.medicine.name, activeMed.display))
        }
        return changes
    }

    private fun checkDosageCount(activeMed: ActiveMedVO, schedule: Schedule): Collection<Change> {

        val changes = ArrayList<Change>()
        val dosage = activeMed.dosage
        val recur = schedule.recur
        val expectedIntakes = dosage.entries.size
        val foundIntakes = schedule.dosages.size
        val sameIntakes = expectedIntakes == foundIntakes

        if (dosage.type == DosageType.GENERAL && recur.freq !in arrayOf(Freq.HOURLY, Freq.DAILY, Freq.WEEKLY)) {
            changes.add(DosageChange())
        } else if (dosage.type == DosageType.DETAILED && recur.hasHourlyFrequency()) {
            changes.add(DosageMissingChange())
        } else if (!sameIntakes) {
            changes.add(IntakeCountChange(expectedIntakes, foundIntakes))
        }
        return changes
    }


    private fun checkDosageEntries(activeMed: ActiveMedVO, schedule: Schedule): Collection<Change> {
        val changes = ArrayList<Change>()
        val entries = activeMed.dosage.entries
        val recur = schedule.recur
        val patient = activeMed.patient
        for (entry in entries) {
            when (entry.repeatType) {

                RepeatType.DURATION -> {
                    // ignore
                }

                RepeatType.PERIOD -> {
                    val entryDose = entry.quantityValue
                    val repeatValue = entry.repeatValue.toInt()
                    val repeatFreq = TimingUtils.freqFromUnitsOfTime(entry.repeatUnits)
                    val freq = recur.freq
                    val interval = recur.interval

                    val dosage = if (schedule.recur.hasHourlyFrequency()) {
                        schedule.getDosage(null)
                    } else {
                        schedule.getDosage(schedule.recur.dailyFixedTimes[0].time)
                    }

                    if (interval != repeatValue || repeatFreq != freq) {
                        changes.add(RepeatPeriodChange(interval, freq, repeatValue, repeatFreq))
                    }

                    if (entryDose != dosage) {
                        changes.add(DosageChange())
                    }
                }

                RepeatType.TIME_OF_DAY -> {
                    val entryDose = entry.quantityValue
                    val entryTime = entry.at
                    var scheduleDose: Double? = null
                    val times = recur.dailyFixedTimes
                    var exists = false

                    times.first { it.time == entryTime }?.let {
                        exists = true
                        scheduleDose = schedule.getDosage(it.time)
                    }

                    if (exists && entryDose != scheduleDose) {
                        changes.add(DosageAtTimeChange(entryTime, entryDose))
                    } else if (!exists) {
                        changes.add(IntakeMissingAtChange())
                    }
                }

                else -> {

                    val entryDose = entry.quantityValue
                    var shouldHaveRoutine = true
                    val routine = when (entry.repeatType) {

                        RepeatType.CD, RepeatType.ACD, RepeatType.PCD -> {
                            DB.routines().findByPatientAndDailyEvent(patient, RepeatType.CD)
                        }
                        RepeatType.CM, RepeatType.ACM, RepeatType.PCM -> {
                            DB.routines().findByPatientAndDailyEvent(patient, RepeatType.CM)
                        }
                        RepeatType.CV, RepeatType.ACV, RepeatType.PCV -> {
                            DB.routines().findByPatientAndDailyEvent(patient, RepeatType.CV)
                        }
                        else -> {
                            shouldHaveRoutine = false
                            null
                        }
                    }

                    if (shouldHaveRoutine && routine == null) {
                        changes.add(IntakeMissingForChange(entry.repeatType))
                    } else if (routine != null && entryDose != schedule.getDosage(routine.time)) {
                        changes.add(DosageForIntakeChange(routine.dailyEventType(), entryDose))
                    }
                }
            }
        }
        return changes
    }

    interface Change {
        fun description(c: Context): String
    }

    private class DosageForIntakeChange(private val r: RepeatType, private val dose: Double) :
        Change {
        override fun description(c: Context): String = c.getString(
            R.string.sched_dosage_at_changed, "\"" + r.getMealContextString(c) + "\"", dose.toString()
        )
    }

    private class DosageAtTimeChange(private val at: LocalTime, private val dose: Double) : Change {
        override fun description(c: Context): String = c.getString(
            R.string.sched_dosage_at_changed, at.toString("HH:mm"), dose.toString()
        )
    }

    private class DosageChange : Change {
        override fun description(c: Context): String = c.getString(R.string.sched_dosage_changed)
    }

    private class DosageMissingChange : Change {
        override fun description(c: Context): String = c.getString(
            R.string.sched_dosage_missing_msg
        )
    }

    private class IntakeCountChange(private val expected: Int, private val found: Int) : Change {
        override fun description(c: Context): String = c.getString(
            R.string.sched_intake_count_msg, found, expected
        )

    }

    private class IntakeMissingForChange(private val r: RepeatType) : Change {
        override fun description(c: Context): String {
            return c.getString(R.string.sched_intake_missing_for) + " \"" + r.getMealContextString(c) + "\""
        }
    }

    private class IntakeMissingAtChange : Change {
        override fun description(c: Context): String = c.getString(R.string.sched_intake_missing_at)
    }

    private class IntakesMovedChange : Change {
        override fun description(c: Context): String = c.getString(R.string.sched_intakes_moved_msg)
    }

    private class RepeatPeriodChange(
        private val expected: Int,
        private val expectedFreq: Freq,
        private val found: Int,
        private val foundFreq: Freq
    ) : Change {
        override fun description(c: Context): String {
            val a = expected.toString() + " " + StringUtils.freqDisplayName(c, expectedFreq)
            val b = found.toString() + " " + StringUtils.freqDisplayName(c, foundFreq)
            return c.getString(R.string.sched_repeat_period_msg, a, b)
        }
    }

    private class ActiveMedChange(val a: String, val b: String) : Change {
        override fun description(c: Context): String {
            return c.getString(R.string.sched_active_med_changed, a, b)
        }
    }

    private class StartDateChange(private val expected: DateTime, private val d: DateTime?) :
        Change {
        override fun description(c: Context): String {
            val dtf = c.getString(R.string.schedule_limits_date_format)
            return c.getString(
                R.string.sched_start_changed,
                if (d != null) d.toString(dtf) else c.getString(R.string.date_none),
                expected.toString(dtf)
            )
        }
    }

    private class EndDateChange(private val expected: DateTime, private val d: DateTime?) : Change {
        override fun description(c: Context): String {
            val dtf = c.getString(R.string.schedule_limits_date_format)
            return c.getString(
                R.string.sched_end_changed,
                if (d != null) d.toString(dtf) else c.getString(R.string.date_none),
                expected.toString(dtf)
            )
        }
    }

}
