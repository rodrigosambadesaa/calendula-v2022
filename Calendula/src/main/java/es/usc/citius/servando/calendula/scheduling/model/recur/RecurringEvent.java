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

package es.usc.citius.servando.calendula.scheduling.model.recur;

import org.dmfs.rfc5545.recur.Freq;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recur.RecurrenceRule.RfcMode;
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
 * Represents an event that can be repeated over time
 */

public class RecurringEvent {

    public static final String TAG = "RecurringEvent";

    public static final RfcMode RFC_MODE = RfcMode.RFC5545_STRICT;
    public static final DateTimeFormatter f = DateTimeFormat.mediumDateTime();
    RecurrenceDateTimeConverter dtc = RecurrenceDateTimeConverter.instance();
    /**
     * Recurring event start date time if exists
     */
    private DateTime startDateTime;

    /**
     * Recurring event end time if exists
     */
    private DateTime endDateTime;

    /**
     * Occurrence days for events that have cyclic repetitions
     */
    private Integer cycleActiveDays;
    /**
     * Rest days for events that have cyclic repetitions
     */
    private Integer cycleInactiveDays;

    /**
     * Encapsulates a recurrence string as defined in RFC 5545 and RFC 2445
     * and provides an API for iterating the instances
     */
    private RecurrenceRule recurrence;
    /**
     * List of daily occurrences, if any. Ex: "[Breakfast Routine, 22:00h]
     */
    private List<DailyFixedTime> dailyFixedTimes;

    /**
     * Use #Builder if possible
     */
    public RecurringEvent() {
        createRecurrenceRule(Freq.DAILY);
    }

    public RecurringEvent(String rule) {
        createRecurrenceRule(rule);
    }

    public String recurrenceRule() {
        return recurrence.toString();
    }

    public Builder edit() {
        return new Builder(this);
    }

    /**
     * Generate a list of event occurrences in a time interval
     *
     * @param from  Interval start date
     * @param until Interval end date (exclusive)
     * @return
     */
    public List<EventTimeInfo> occurrencesIn(DateTime from, DateTime until) {
        List<EventTimeInfo> occurrences = new ArrayList<>();
        DateTime correctedFrom;
        if (hasHourlyFrequency()) {
            correctedFrom=from;
        }
        else {
            correctedFrom=from.withTimeAtStartOfDay();
        }
        RecurrenceRuleIterator iterator = getRecurrenceIterator(correctedFrom, true);

        while (iterator.hasNext()) {
            DateTime next = dtc.convert(iterator.nextDateTime());
            LogUtil.d(TAG, "Next: " + next.toString(f));

            // stop when we reach the end of the requested period
            // or if the next occurrence is after end
            if (isEqualOrAfter(next, until) || isEqualOrAfterEnd(next)) {
                break;
            }

            // for cyclic events, skip inactive dates
            if (isCyclic() && !isCycleActiveAt(next)) {
                continue;
            }

            // if the event has daily fixed times, instead of adding the day to the list,
            // we add one instance of each of them for every day the event has occurrences
            if (hasDailyFixedTimes()) {
                for (DailyFixedTime rep : dailyFixedTimes) {
                    DateTime time = next
                            .withHourOfDay(rep.getTime().getHourOfDay())
                            .withMinuteOfHour(rep.getTime().getMinuteOfHour());

                    // ensure daily times are before #until and before #endDateTime
                    if (time.isBefore(from)) {
                        LogUtil.d(TAG, "Next fixedTime is out of range, continue. [" + time.toString(f) + "]");
                        continue;
                    }
                    else if (isEqualOrAfter(time, until) || isEqualOrAfterEnd(time)) {
                        LogUtil.d(TAG, "Next fixedTime is out of range, stop. [" + time.toString(f) + "]");
                        break;
                    }
                    occurrences.add(new EventTimeInfo(time, rep.offset()));
                }
            } else {
                // otherwise, we add the next occurrence to the list
                occurrences.add(new EventTimeInfo(next));
            }
        }
        return occurrences;
    }

    public RecurrenceRuleIterator getRecurrenceIterator(DateTime from, boolean sync) {
        RecurrenceRuleIterator iterator;
        // if there is a start dateTime, we should always start
        // iterating from it to ensure proper times are returned
        DateTime dtStart = startDateTime;
        if (hasStart()) {
            iterator = recurrence.iterator(dtc.convert(startDateTime));
            // then, if the interval start is after the schedule
            // start, ignore all the events in the middle
            if (from.isAfter(startDateTime)) {
                iterator.fastForward(dtc.convert(from));
                dtStart = from;
            }
        } else {
            iterator = recurrence.iterator(dtc.convert(from));
            dtStart = from;
        }
        // sync iterator start with rule if necessary
        if (sync) {
            syncDtStartWithRule(dtStart, iterator);
        }
        return iterator;
    }

    public boolean isEqualOrAfter(DateTime time, DateTime limit) {
        return time.isAfter(limit) || time.equals(limit);
    }

    public boolean isEqualOrAfterEnd(DateTime time) {
        if (hasEnd()) {
            return time.isAfter(endDateTime) || time.equals(endDateTime);
        }
        return false;
    }

    public boolean hasOccurrencesAt(LocalDate when) {
        DateTime t = when.toDateTimeAtStartOfDay();
        if (isCyclic()) {
            return isCycleActiveAt(t);
        }
        // iterator start is not synchronized to avoid starting always
        // on a valid date. Due to this, we have to manually check the
        // weekday of the generated instance to ensure is supported
        // be the recurrence rule definition, if the byDayPart exists.
        RecurrenceRuleIterator it = getRecurrenceIterator(t, false);
        if (it.hasNext()) {
            org.dmfs.rfc5545.DateTime dt = it.nextDateTime();
            DateTime next = dtc.convert(dt);
            if (recurrence.getByDayPart() != null) {
                for (RecurrenceRule.WeekdayNum wd : recurrence.getByDayPart()) {
                    if (wd.weekday.ordinal() == dt.getDayOfWeek()) {
                        return next.isBefore(t.plusDays(1)) && !isEqualOrAfterEnd(next);
                    }
                }
            } else {
                return next.isBefore(t.plusDays(1)) && !isEqualOrAfterEnd(next);
            }
        }
        return false;
    }

    /**
     * Hourly occurrence utils
     **/

    public boolean hasHourlyFrequency() {
        return recurrence.getFreq().equals(Freq.HOURLY);
    }

    /**
     * Daily occurrence utils
     **/



    public boolean hasDailyFixedTimes() {
        return dailyFixedTimes != null && dailyFixedTimes.size() > 0;
    }

    public void addDailyFixedTime(DailyFixedTime o) {
        if (dailyFixedTimes == null) {
            dailyFixedTimes = new ArrayList<>();
        }
        dailyFixedTimes.add(o);
    }

    public int getInterval(){
        return recurrence.getInterval();
    }

    public void removeDailyFixedTime(DailyFixedTime o) {
        dailyFixedTimes.remove(o);
    }

    public List<DailyFixedTime> getDailyFixedTimes() {
        return dailyFixedTimes;
    }

    /**
     * Start and End utils
     **/

    public boolean hasStart() {
        return startDateTime != null;
    }

    public boolean hasEnd() {
        return endDateTime != null;
    }

    public DateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(DateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public DateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(DateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    /**
     * Cyclic recurrences
     **/

    public Integer getCycleActiveDays() {
        return cycleActiveDays;
    }

    public void setCycleActiveDays(Integer cycleActiveDays) {
        this.cycleActiveDays = cycleActiveDays;
    }

    public Integer getCycleInactiveDays() {
        return cycleInactiveDays;
    }

    public void setCycleInactiveDays(Integer cycleInactiveDays) {
        this.cycleInactiveDays = cycleInactiveDays;
    }

    public boolean isCyclic() {
        return cycleActiveDays != null && cycleInactiveDays != null;
    }

    public boolean[] byDay() {
        boolean[] days = new boolean[7];
        if(recurrence.hasPart(RecurrenceRule.Part.BYDAY)) {
            for (RecurrenceRule.WeekdayNum w : recurrence.getByDayPart()) {
                int position = w.weekday.ordinal() == 0 ? 6 : w.weekday.ordinal() - 1;
                days[position] = true;
            }
        }
        return days;
    }

    public List<WeekDay> weekDays() {
        List<WeekDay> days = new ArrayList<>();
        if(recurrence.hasPart(RecurrenceRule.Part.BYDAY)) {
            for (RecurrenceRule.WeekdayNum w : recurrence.getByDayPart()) {
                int position = w.weekday.ordinal() == 0 ? 6 : w.weekday.ordinal() - 1;
                days.add(WeekDay.fromValue(position));
            }
        }
        return days;
    }

    public boolean allWeekdaysSelected(){
        return recurrence.getByDayPart() == null || recurrence.getByDayPart().size() == 7;
    }

    public void setRecurrence(RecurrenceRule recurrence) {
        this.recurrence = recurrence;
    }

    protected void createRecurrenceRule(String rr) {
        try {
            recurrence = new RecurrenceRule(rr, RFC_MODE);
        } catch (InvalidRecurrenceRuleException e) {
            throw new IllegalArgumentException("Invalid recurrence rule", e);
        }
    }

    /**
     * Avoid the first instance when #dtStart is not synchronized with the rule.
     * <p>
     * RFC 5545 Section 3.8.5.3 states:
     * The "DTSTART" property defines the first instance in the recurrence set.
     * The "DTSTART" property value SHOULD be synchronized with the recurrence rule,
     * if specified. The recurrence set generated with a "DTSTART" property
     * value not synchronized with the recurrence rule is undefined.
     * <p>
     * i.e., If #startDateTime is Monday and the rule repeats only on Thursdays, #dtStart will be
     * always the first instance provided by the iterator, which is not correct for us. In this
     * case we should synchronize DTSTART to begin the iteration on the first thursday.
     *
     * @param dtStart
     * @param iterator
     */
    private void syncDtStartWithRule(DateTime dtStart, RecurrenceRuleIterator iterator) {
        List<RecurrenceRule.WeekdayNum> byDay = recurrence.getByDayPart();
        int startWeekDay = dtStart.getDayOfWeek(); // 1 to 7
        if (byDay != null) {
            int ruleWeekDay;
            // compare each weekday of the rule with the #dtStart weekday
            for (int i = 0; i < byDay.size(); i++) {
                ruleWeekDay = byDay.get(i).weekday.ordinal();
                if (ruleWeekDay == 0) ruleWeekDay = 7; // byDay start on SU (0 to 6)

                LogUtil.d(TAG, "RWD: " + ruleWeekDay + ", SWD: " + startWeekDay);
                // stop if there is a match, rule and dtStart are in sync
                if (ruleWeekDay == startWeekDay) {
                    LogUtil.d(TAG, "SameDay");
                    return;
                }
                // if the first weekday of the rule is after #dtStart, advance to it
                else if (ruleWeekDay > startWeekDay) {
                    LogUtil.d(TAG, ruleWeekDay - startWeekDay + " days after ");
                    iterator.fastForward(dtc.convert(dtStart.plusDays(ruleWeekDay - startWeekDay)));
                    return;
                }
            }
            // at this point, there are no weekdays for this rule after
            // dtStart, so we need to move to the the first of the next week
            ruleWeekDay = byDay.get(0).weekday.ordinal();
            if (ruleWeekDay == 0) ruleWeekDay = 7; // byDay start on SU (0 to 6)
            int gap = startWeekDay - ruleWeekDay; // rule week day is before
            LogUtil.d(TAG, 7 - gap + " days after (on the next week)");
            iterator.fastForward(dtc.convert(dtStart.plusDays(7 - gap)));
        }
    }

    private void createRecurrenceRule(Freq freq) {
        recurrence = new RecurrenceRule(freq, RFC_MODE);
    }

    private boolean isCycleActiveAt(DateTime date) {
        if (date.isBefore(startDateTime)) return false;
        int cycleLength = cycleActiveDays + cycleInactiveDays;
        Interval interval = new Interval(startDateTime, date);
        int days = (int) interval.toDuration().getStandardDays();
        int cyclesUntilNow = days / cycleLength;
        DateTime currentCycleStart = startDateTime.plusDays(cyclesUntilNow * cycleLength);
        interval = new Interval(currentCycleStart, currentCycleStart.plusDays(cycleActiveDays));
        return interval.contains(date);
    }

    private void addDailyFixedTimes(List<DailyFixedTime> dailyFixedTimes) {
        for (DailyFixedTime d : dailyFixedTimes) {
            this.addDailyFixedTime(d);
        }
    }


    public enum WeekDay {

        MO(0, "MO"),
        TU(1, "TU"),
        WE(2, "WE"),
        TH(3, "TH"),
        FR(4, "FR"),
        SA(5, "SA"),
        SU(6, "SU");

        String code;
        int value;

        WeekDay(int value, String code) {
            this.value = value;
            this.code = code;
        }

        public int value() {
            return value;
        }

        public String code() {
            return code;
        }

        RecurrenceRule.WeekdayNum toWeekDayNum() {
            try {
                return RecurrenceRule.WeekdayNum.valueOf(code);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static WeekDay fromValue(int v) {
            switch (v) {
                case 0:
                    return MO;
                case 1:
                    return TU;
                case 2:
                    return WE;
                case 3:
                    return TH;
                case 4:
                    return FR;
                case 5:
                    return SA;
                case 6:
                    return SU;
                default:
                    throw new RuntimeException("Invalid value" + v);
            }
        }

        public static WeekDay[] ALL = new WeekDay[]{MO, TU, WE, TH, FR, SA, SU};
    }

    public static class Builder {

        private RecurringEvent original;
        private RecurringEvent event;
        private boolean silent = false;

        public Builder() {
            this.event = new RecurringEvent();
        }

        public Builder(RecurringEvent recurringEvent) {
            // create a copy of the original recurrence with the same values
            original = recurringEvent;
            event = new RecurringEvent();
            copyFieldValues(original, event);
        }

        public Builder from(DateTime start) {
            event.setStartDateTime(start);
            return this;
        }

        public Builder from(LocalDate start) {
            event.setStartDateTime(start.toDateTimeAtStartOfDay());
            return this;
        }


        public Builder to(DateTime end) {
            event.setEndDateTime(end);
            return this;
        }

        public Builder to(LocalDate end) {
            event.setEndDateTime(end != null ? end.toDateTimeAtStartOfDay().plusDays(1).minusSeconds(1) : null);
            return this;
        }

        public Builder repeatDaily() {
            event.recurrence.setInterval(1);
            event.recurrence.setFreq(Freq.DAILY, silent);
            return this;
        }

        public Builder repeatEvery(int interval, Freq freq) {
            event.recurrence.setInterval(interval);
            event.recurrence.setFreq(freq, silent);
            return this;
        }

        public Builder atFixedTime(LocalTime t) {
            DailyFixedTime dft = new DailyFixedTime(DailyFixedTime.ReferenceType.ABSOLUTE, t);
            this.event.addDailyFixedTime(dft);
            return this;
        }

        public Builder atFixedTime(DailyFixedTime t) {
            this.event.addDailyFixedTime(t);
            return this;
        }

        public Builder dailyRepeatTimes(List<DailyFixedTime> times) {
            if (this.event.dailyFixedTimes == null) {
                this.event.dailyFixedTimes = new ArrayList<>();
            }
            this.event.dailyFixedTimes.addAll(times);
            return this;
        }

        public Builder clearDailyFixedTimes() {
            if (this.event.dailyFixedTimes != null) {
                this.event.dailyFixedTimes.clear();
            }
            return this;
        }

        public Builder clearCycle() {
            this.event.setCycleActiveDays(null);
            this.event.setCycleInactiveDays(null);
            return this;
        }


        public Builder repeatCyclic(Integer activeDays, Integer inactiveDays) {
            repeatDaily();
            this.event.setCycleActiveDays(activeDays);
            this.event.setCycleInactiveDays(inactiveDays);
            return this;
        }

        public Builder repeatWeekdays(WeekDay... weekDays) {
            if(weekDays.length == 0)
                return clearWeekdays();
            else
                return repeatWeekdays(Arrays.asList(weekDays));
        }

        public Builder clearWeekdays(){
            return repeatWeekdays(WeekDay.ALL);
        }

        public Builder repeatWeekdays(List<WeekDay> weekDays) {
            List<RecurrenceRule.WeekdayNum> wdn = new ArrayList<>(weekDays.size());
            for (WeekDay w : weekDays) {
                wdn.add(w.toWeekDayNum());
            }
            if(!wdn.isEmpty()) {
                event.recurrence.setByDayPart(wdn);
            }else{
                event.recurrence.setByDayPart(null);
            }
            return this;
        }

        public Builder repeatWeekdays(boolean[] weekDays) {
            List<WeekDay> wd = new ArrayList<>();
            for (int i = 0; i < weekDays.length; i++) {
                if (weekDays[i]) {
                    wd.add(WeekDay.fromValue(i));
                }
            }
            if(!wd.isEmpty()){
                repeatWeekdays(wd);
            }else{
                event.recurrence.setByDayPart(null);
            }
            return this;
        }

        public void commit() throws InvalidRecurringEventException {
            // copy values from edited recurrence to the original one
            copyFieldValues(build(), original);
        }

        public RecurringEvent build() throws InvalidRecurringEventException {

            // TODO: 6/9/17 Add more checks
            if (event.cycleActiveDays != null || event.cycleInactiveDays != null) {
                fail(event.startDateTime == null,
                        "Start can not be null on cyclic events");
                fail(event.cycleActiveDays == null || event.cycleActiveDays < 1,
                        "ActiveDays can not be null on cyclic events");
                fail(event.cycleInactiveDays == null || event.cycleInactiveDays < 1,
                        "InactiveDays can not be null on cyclic events");
            }

            if (event.recurrence.getInterval() > 0) {
                fail(!event.hasStart(), "Events that repeat by an interval must have an start");
            }

            return event;
        }

        private void fail(boolean condition, String message) throws InvalidRecurringEventException {
            if (condition) {
                throw new InvalidRecurringEventException("Inconsistent event. " + message);
            }
        }

        private void copyFieldValues(RecurringEvent from, RecurringEvent to) {
            try {
                to.setRecurrence(new RecurrenceRule(from.recurrence.toString(), RFC_MODE));
                to.setStartDateTime(from.hasStart()? from.getStartDateTime().toDateTime() : null);
                to.setEndDateTime(from.hasEnd() ? from.getEndDateTime().toDateTime() : null);
                to.setCycleActiveDays(from.getCycleActiveDays());
                to.setCycleInactiveDays(from.getCycleInactiveDays());
                if (from.dailyFixedTimes != null && from.dailyFixedTimes.size() > 0) {
                    to.dailyFixedTimes = new ArrayList<>(from.getDailyFixedTimes());
                } else {
                    to.dailyFixedTimes = null;
                }
            }catch (Exception e){
                throw new RuntimeException("Error copying field values", e);
            }
        }
    }

    /**
     * Custom exception to be thrown when a
     */
    public static class InvalidRecurringEventException extends Exception {
        public InvalidRecurringEventException(String message) {
            super(message);
        }
    }

    public Freq getFreq() {
        return recurrence.getFreq();
    }

    @Override
    public String toString() {
        return "RecurringEvent{" +
                "dtc=" + dtc +
                ", startDateTime=" + startDateTime +
                ", endDateTime=" + endDateTime +
                ", cycleActiveDays=" + cycleActiveDays +
                ", cycleInactiveDays=" + cycleInactiveDays +
                ", recurrence=" + recurrence +
                ", dailyFixedTimes=" + (dailyFixedTimes != null ? dailyFixedTimes.size() : "0") +
                '}';
    }

    public class EventTimeInfo{

        private DateTime time;
        private EventInstance.EventOffset offset;

        public EventTimeInfo(DateTime time) {
            this.time = time;
        }

        public EventTimeInfo(DateTime time, EventInstance.EventOffset offset) {
            this.time = time;
            this.offset = offset;
        }

        public EventInstance.EventOffset offset(){
            return offset;
        }

        public DateTime time() {
            return time;
        }
    }
}
