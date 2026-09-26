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

package es.usc.citius.servando.calendula.healthcareprovider.util;

import android.util.Pair;

import org.dmfs.rfc5545.recur.Freq;
import org.hl7.fhir.dstu3.model.Timing;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import es.usc.citius.servando.calendula.healthcareprovider.jobs.UpdateMedicationHelper;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
 * Maps provider dosage data to local schedules.
 */
public class ScheduleCreator {

    private static final String TAG = "ScheduleCreator";

    private static final EnumSet<RepeatType> BEFORE_MEAL =
            EnumSet.of(RepeatType.ACD, RepeatType.ACM, RepeatType.ACV);
    private static final EnumSet<RepeatType> AFTER_MEAL =
            EnumSet.of(RepeatType.PCD, RepeatType.PCM, RepeatType.PCV);

    public static Pair<ScheduleMappingResult, Schedule> fromActiveMed(Long activeMedId) {
        try {
            ActiveMedEntity am = DB.healthcareProviderDB().activeMeds().findById(activeMedId);
            ActiveMedVO vo = ActiveMedVO.forEntity(am);
            Patient patient = vo.getPatient();

            Medicine m;
            Map<Long, Double> dosages;
            ScheduleMappingResult result;


            if (vo.getActiveMedType().equals(ActiveMedType.DCPF)) {
                m = DB.medicines().findByGroupAndPatient(vo.getCode(), vo.getPatient());
            } else {
                m = DB.medicines().findByCnAndPatient(vo.getCode(), vo.getPatient());
            }

            // med is not in the med-kit
            if (m == null) {
                m = UpdateMedicationHelper.addMedicineToMedKit(am, patient);
            }

            RecurringEvent.Builder rb = new RecurringEvent.Builder();

            DosageVO dosage = vo.getDosage();
            switch (dosage.getType()) {
                case GENERAL:
                    DB.healthcareProviderDB().dosages().refresh(dosage.entity());
                    Collection<DosageEntryEntity> entries = dosage.entity().getEntries();
                    final int entryCount = entries != null ? entries.size() : 0;
                    LogUtil.d(TAG, "General schedule, entries: " + entryCount);
                    if (entries == null || entries.isEmpty()) {
                        throw new IllegalArgumentException("Dosage has no entries!");
                    }
                    DosageEntryEntity e = entries.iterator().next();
                    dosages = new HashMap<>();
                    Timing.UnitsOfTime units = e.getRepeatUnits();
                    if (units == null) {
                        throw new IllegalArgumentException("General dosage has no repeat units");
                    }
                    Freq freq;
                    switch (units) {
                        case H:
                            freq = Freq.HOURLY;
                            break;
                        case D:
                            freq = Freq.DAILY;
                            break;
                        case WK:
                            freq = Freq.WEEKLY;
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported repeat units " + units + " for general schedules");
                    }

                    if (e.getRepeatValue() == null || e.getRepeatValue() <= 0) {
                        throw new IllegalArgumentException("General dosage has invalid repeat value");
                    }
                    rb.repeatEvery(e.getRepeatValue().intValue(), freq);

                    if (freq != Freq.HOURLY) {
                        Routine r = DB.routines().findByPatientAndDailyEvent(patient, RepeatType.CD);
                        if (r == null) {
                            throw new IllegalStateException("Lunch routine is unavailable for general schedule");
                        }
                        rb.atFixedTime(new DailyFixedTime(r.getId(), DailyFixedTime.ReferenceType.ROUTINE));
                        dosages.put(r.getId(), e.getQuantityValue());
                    } else {
                        dosages.put(Schedule.DEFAULT_DOSAGE_KEY, e.getQuantityValue());
                    }
                    result = ScheduleMappingResult.COMPLETE;
                    break;
                case DETAILED:
                    dosages = new HashMap<>();
                    rb.repeatEvery(1, Freq.DAILY);
                    DB.healthcareProviderDB().dosages().refresh(dosage.entity());
                    List<DosageEntryEntity> dEntries = DB.healthcareProviderDB().dosageEntries().findBy(DosageEntryEntity.COLUMN_DOSAGE, dosage.entity().getId());
                    for (DosageEntryEntity d : dEntries) {
                        handleDetailedEntry(patient, d, dosages, rb);
                    }
                    result = ScheduleMappingResult.COMPLETE;
                    break;
                default:
                    // set a default schedule
                    rb.repeatEvery(8, Freq.HOURLY);
                    dosages = new HashMap<>();
                    dosages.put(Schedule.DEFAULT_DOSAGE_KEY, 1d);
                    result = ScheduleMappingResult.PARTIAL;
            }

            // set start
            if (am.getValidityStart() != null) {
                rb.from(am.getValidityStart());
            } else {
                rb.from(DateTime.now());
            }

            // set end
            if (am.getValidityEnd() != null) {
                rb.to(am.getValidityEnd());
            }

            Schedule s = create(rb, dosages, m);
            s.setActiveMedId(activeMedId);
            if (result == ScheduleMappingResult.COMPLETE) {
                s.addState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL);
            }
            return new Pair<>(result, s);
        } catch (Exception e) {
            LogUtil.e(TAG, "An error occurred while mapping active med to schedule", e);
            return new Pair<>(ScheduleMappingResult.NONE, null);
        }

    }

    public static Schedule create(RecurringEvent.Builder builder, Map<Long, Double> dosages, Medicine m) {
        try {
            Schedule s = new Schedule(builder.build());
            s.setDosages(dosages);
            s.setMedicine(m);
            s.setPatient(m.getPatient());

            LogUtil.d(TAG, "Generating schedule with dosages " + dosages);

            if (s.getRecur().hasDailyFixedTimes()) {
                for (DailyFixedTime t : s.getRecur().getDailyFixedTimes()) {
                    LogUtil.d(TAG, "create: " + t.getTime().toString("HH:mm") + ": " + s.getDosage(t.getTime()));
                }
            }

            return s;
        } catch (Exception e) {
            LogUtil.e(TAG, "Error creating schedule", e);
            throw new IllegalArgumentException("Error creating schedule", e);
        }
    }

    private static void handleDetailedEntry(Patient patient, DosageEntryEntity d, Map<Long, Double> dosages, RecurringEvent.Builder rb) {

        if (d == null || d.getRepeatType() == null) {
            LogUtil.w(TAG, "Ignoring detailed dosage entry without repeat type");
            return;
        }

        Routine r = null;
        RepeatType repeatType = d.getRepeatType();
        switch (repeatType) {
            case CD:
            case ACD:
            case PCD:
                r = DB.routines().findByPatientAndDailyEvent(patient, RepeatType.CD);
                break;
            case CM:
            case ACM:
            case PCM:
                r = DB.routines().findByPatientAndDailyEvent(patient, RepeatType.CM);
                break;
            case CV:
            case ACV:
            case PCV:
                r = DB.routines().findByPatientAndDailyEvent(patient, RepeatType.CV);
                break;
            case TIME_OF_DAY:
                if (d.getAt() == null) {
                    LogUtil.w(TAG, "Ignoring time-of-day dosage entry without a time");
                    return;
                }
                r = DB.routines().findByPatientAndTime(patient, d.getAt());
                if (r == null) {
                    r = new Routine(d.getAt(), null);
                    r.setPatient(patient);
                    DB.routines().save(r);
                }
                break;
            default:
                break;
        }

        if (r != null) {
            DailyFixedTime time = new DailyFixedTime(r.getId(), DailyFixedTime.ReferenceType.ROUTINE);
            time.setOffset(eventOffsetForRepeatType(repeatType));
            dosages.put(r.getId(), d.getQuantityValue());
            rb.atFixedTime(time);
        }

    }

    static EventInstance.EventOffset eventOffsetForRepeatType(RepeatType repeatType) {
        if (repeatType == null) {
            return EventInstance.EventOffset.NONE;
        }
        if (BEFORE_MEAL.contains(repeatType)) {
            return EventInstance.EventOffset.BEFORE;
        }
        if (AFTER_MEAL.contains(repeatType)) {
            return EventInstance.EventOffset.AFTER;
        }
        return EventInstance.EventOffset.NONE;
    }

    /**
     * Represents schedule mapping result from active meds
     */
    public enum ScheduleMappingResult {
        // All necessary information found
        COMPLETE,
        // Some information found
        PARTIAL,
        // No possible mapping
        NONE
    }

}
