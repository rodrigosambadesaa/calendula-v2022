/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import static org.junit.Assert.*;

public class ScheduleDoseGuardTest {
    private Schedule hourly() throws Exception {
        return new Schedule(new RecurringEvent.Builder()
                .from(DateTime.now()).repeatEvery(8, Freq.HOURLY).build());
    }

    private void rejected(Schedule schedule) {
        boolean rejected = false;
        try {
            ScheduleDoseGuard.requireSafeAutomatedDoseMap(schedule);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue("Unsafe prescription must not generate automatic intakes", rejected);
    }

    @Test
    public void hourlyAutomaticScheduleRequiresOneFinitePositiveDefaultDose() throws Exception {
        Schedule schedule = hourly();
        for (Double bad : new Double[]{
                null, 0d, -1d, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            Map<Long, Double> m = new HashMap<>();
            m.put(Schedule.DEFAULT_DOSAGE_KEY, bad);
            schedule.setDosages(m);
            rejected(schedule);
        }
        schedule.setDosage(0.5d);
        ScheduleDoseGuard.requireSafeAutomatedDoseMap(schedule);
        Map<Long, Double> wrong = new HashMap<>();
        wrong.put(42L, 1d);
        schedule.setDosages(wrong);
        rejected(schedule);
    }

    @Test
    public void fixedTimeRecurrenceRequiresMatchingDosageReferences() throws Exception {
        DailyFixedTime time = new DailyFixedTime(
                900045L, DailyFixedTime.ReferenceType.ROUTINE);
        Schedule schedule = new Schedule(new RecurringEvent.Builder()
                .from(DateTime.now()).repeatEvery(1, Freq.DAILY)
                .atFixedTime(time).build());
        schedule.setDosages(new HashMap<>());
        rejected(schedule);
        Map<Long, Double> wrong = new HashMap<>();
        wrong.put(900046L, 1d);
        schedule.setDosages(wrong);
        rejected(schedule);
        Map<Long, Double> correct = new HashMap<>();
        correct.put(900045L, 0.5d);
        schedule.setDosages(correct);
        ScheduleDoseGuard.requireSafeAutomatedDoseMap(schedule);
    }

    @Test
    public void pausedOrReviewRequiredScheduleRetainsUntrustedHistoricalDoseMap() throws Exception {
        Schedule paused = hourly();
        paused.setDosages(null);
        paused.addState(Schedule.ScheduleState.BLOCKED);
        ScheduleDoseGuard.requireSafeAutomatedDoseMap(paused);

        Schedule review = hourly();
        review.setDosages(new HashMap<>());
        review.addState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE);
        ScheduleDoseGuard.requireSafeAutomatedDoseMap(review);
    }

    @Test
    public void activeScheduleWithoutRecurrenceOrDosageIsRejected() throws Exception {
        rejected(new Schedule());
        rejected(hourly());
    }
}
