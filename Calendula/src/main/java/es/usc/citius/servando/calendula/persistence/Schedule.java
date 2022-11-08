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

package es.usc.citius.servando.calendula.persistence;

import android.content.Context;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.persistence.typeSerializers.DosageMapPersister;
import es.usc.citius.servando.calendula.persistence.typeSerializers.RecurringEventPersister;
import es.usc.citius.servando.calendula.persistence.typeSerializers.ScheduleStatePersister;
import es.usc.citius.servando.calendula.scheduling.ScheduleDisplayUtils;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventProvider;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import es.usc.citius.servando.calendula.util.LogUtil;


@DatabaseTable(tableName = "NewSchedules")
public class Schedule implements EventProvider {

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_MEDICINE = "Medicine";
    public static final String COLUMN_DOSAGE = "Dosage";
    public static final String COLUMN_SCANNED = "Scanned";
    public static final String COLUMN_PATIENT = "Patient";
    public static final String COLUMN_STATE = "State";
    public static final String COLUMN_RECUR = "Recur";
    public static final String BOUND_TO = "BoundTo";
    public static final Long DEFAULT_DOSAGE_KEY = -10l;
    private static final String TAG = "Schedule";
    // TODO Try to keep accessors interface
    @DatabaseField(columnName = COLUMN_DOSAGE, persisterClass = DosageMapPersister.class)
    Map<Long, Double> dosages;
    @DatabaseField(columnName = BOUND_TO)
    Long activeMedId;
    @DatabaseField(columnName = COLUMN_ID, generatedId = true)
    private Long id;
    @DatabaseField(columnName = COLUMN_MEDICINE, foreign = true, foreignAutoRefresh = true)
    private Medicine medicine;

    //TODO: Remove this attribute from model
    @DatabaseField(columnName = COLUMN_SCANNED)
    private boolean scanned;

    @DatabaseField(columnName = COLUMN_PATIENT, foreign = true, foreignAutoRefresh = true)
    private Patient patient;
    @DatabaseField(columnName = COLUMN_STATE, persisterClass = ScheduleStatePersister.class)
    private EnumSet<ScheduleState> state = EnumSet.noneOf(ScheduleState.class);

    @DatabaseField(columnName = COLUMN_RECUR, persisterClass = RecurringEventPersister.class)
    private RecurringEvent recur;

    public Schedule() {
    }

    public Schedule(RecurringEvent evt) {
        this.recur = evt;
    }

    public Schedule(Medicine medicine) {
        this.medicine = medicine;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Medicine getMedicine() {
        return medicine;
    }

    public Map<Long, Double> getDosages() {
        return dosages;
    }

    public int getDosageCount() {
        return dosages != null ? dosages.size() : 0;
    }

    public void setMedicine(Medicine medicine) {
        this.medicine = medicine;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public RecurringEvent getRecur() {
        return recur;
    }

    public Double getDosage(LocalTime time) {

        LogUtil.d(TAG, "Get dosages: " + time + ", " + (dosages != null ? dosages.size() : 0));
        if (dosages == null || dosages.isEmpty()) {
            return null;
        } else if (recur != null && getRecur().hasHourlyFrequency()) {
            return getDosages().get(DEFAULT_DOSAGE_KEY);
        } else if (recur != null && recur.hasDailyFixedTimes()) {
            if (recur.getDailyFixedTimes().size() != dosages.size()) {
                LogUtil.e(TAG, "Inconsistent schedule. Dosage size must equals daily fixed times");
                return null;
            }
            for (int i = 0; i < recur.getDailyFixedTimes().size(); i++) {
                DailyFixedTime dft = recur.getDailyFixedTimes().get(i);
                if (dft.getTime().equals(time)) {
                    return dosages.get(dft.getReference());
                }
            }
        }
        return null;
    }

    public void setDosages(Map<Long, Double> dosages) {
        this.dosages = dosages;
    }

    public void setDosages(List<Double> dosageList) {
        Map<Long, Double> newDosages;
        if (recur.hasHourlyFrequency() && dosageList.size() == 1) {
            newDosages = new HashMap<>(1);
            newDosages.put(DEFAULT_DOSAGE_KEY, dosageList.get(0));
        } else if (dosageList.size() >= getRecur().getDailyFixedTimes().size()) {
            List<DailyFixedTime> times = recur.getDailyFixedTimes();
            newDosages = new HashMap<>(times.size());
            int i = 0;
            for (DailyFixedTime t : times) {
                newDosages.put(t.getReference(), dosageList.get(i++));
            }
        } else {
            throw new RuntimeException("Invalid dosages");
        }
        this.dosages = newDosages;
    }

    public void setDosage(Double d) {
        this.dosages = new HashMap<>();
        this.dosages.put(DEFAULT_DOSAGE_KEY, d);
    }

    @Override
    public EventType getEventType() {
        return EventType.MEDICATION_INTAKE;
    }

    @Override
    public Collection<EventInstance> getEventsBetween(DateTime start, DateTime end) {
        Collection<EventInstance> events = new ArrayList<>();
        if (!eventGenerationDisabled()) {
            for (RecurringEvent.EventTimeInfo t : recur.occurrencesIn(start, end)) {
                EventInstance e = new EventInstance(t.time(), getEventType());
                e.setRef(this.id);
                e.setPatient(this.patient);
                e.setOffset(t.offset());
                e.addParam(EventInstance.PARAM_DOSE,getDosage(t.time().toLocalTime()));
                events.add(e);
            }
        }
        return events;
    }

    @Override
    public Boolean hasEventsAt(LocalDate date) {
        if (state.contains(ScheduleState.BLOCKED)) {
            return false;
        }
        return recur.hasOccurrencesAt(date);
    }

    public boolean isScanned() {
        return scanned;
    }

    public boolean hasState(ScheduleState s) {
        return state != null ? state.contains(s) : false;
    }

    public void addState(ScheduleState state) {
        this.state.add(state);
    }

    public EnumSet<ScheduleState> getState() {
        return state;
    }

    public void removeState(ScheduleState state) {
        this.state.remove(state);
    }


    public void setRecur(RecurringEvent recur) {
        this.recur = recur;
    }

    public boolean isBoundToActiveMed() {
        return activeMedId != null;
    }

    public Long getActiveMedId() {
        return activeMedId;
    }

    public void setActiveMedId(Long activeMedId) {
        this.activeMedId = activeMedId;
    }

    public String intakeTimesString(Context ctx) {
        if (recur.hasHourlyFrequency()) {
            return ctx.getString(R.string.repeat_every_tostr, String.valueOf(getRecur().getInterval()), ctx.getString(R.string.hours));
        } else if (recur.hasDailyFixedTimes()) {
            return ScheduleDisplayUtils.getTimesByDayStr(recur.getDailyFixedTimes().size(), ctx);
        } else {
            return "TBD";
        }
    }

    public String intakeDaysString(Context ctx) {
        if (recur.hasHourlyFrequency()) {
            return ctx.getString(R.string.every_day);
        } else if (recur.isCyclic()) {
            return getRecur().getCycleActiveDays() + " + " + recur.getCycleInactiveDays();
        } else if (Freq.DAILY.equals(getRecur().getFreq()) && getRecur().getInterval() > 1) {
            return ctx.getString(R.string.repeat_every_tostr, String.valueOf(getRecur().getInterval()), ctx.getString(R.string.schedule_repeat_frequency_days));
        } else {
            String result = "";
            if (Freq.WEEKLY.equals(getRecur().getFreq()) && getRecur().getInterval() > 1) {
                result = ctx.getString(R.string.repeat_every_tostr, String.valueOf(getRecur().getInterval()), ctx.getString(R.string.schedule_repeat_frequency_weeks));
                result += ", ";
            }
            return result + ScheduleDisplayUtils.getByDayStr(recur.byDay(), ctx);
        }
    }

    @Override
    public String toString() {
        return "Schedule{" +
                "id=" + id +
                ", recur=" + recur.toString() +
                '}';
    }

    private boolean eventGenerationDisabled() {
        return state.contains(ScheduleState.BLOCKED) ||
                state.contains(ScheduleState.PENDING_REVIEW_AFTER_UPDATE) ||
                state.contains(ScheduleState.PENDING_REVIEW_AFTER_DELETE);
    }

    public enum ScheduleState {
        BLOCKED("BLOCKED"),
        PENDING_REVIEW_AFTER_UPDATE("PENDING_REVIEW_AFTER_UPDATE"),
        PENDING_REVIEW_AFTER_DELETE("PENDING_REVIEW_AFTER_DELETE"),
        DIFFERS_FROM_OFFICIAL("DIFFERS_FROM_OFFICIAL"),
        CREATED_FROM_OFFICIAL("CREATED_FROM_OFFICIAL");

        String value;

        ScheduleState(String value){
            this.value = value;
        }

        public String value() {
            return value;
        }

        @Override
        public String toString() {
            return value();
        }
    }
}
