/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import java.util.Map;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;

/** Fail closed: a manually scheduled dose must have an exact positive quantity. */
public final class ScheduleDoseGuard {
    private ScheduleDoseGuard() {
    }

    public static void requireSafeAutomatedDoseMap(Schedule schedule) {
        if (schedule == null || schedule.getRecur() == null) {
            throw new IllegalArgumentException("Scheduled treatment has no recurrence");
        }
        // Explicitly paused / review-required schedules do not emit intakes.
        // Their previous dosage map may be missing or damaged; preserve it
        // for user review instead of altering clinical history.
        if (schedule.hasState(Schedule.ScheduleState.BLOCKED)
                || schedule.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE)
                || schedule.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_DELETE)) {
            return;
        }

        Map<Long, Double> doses = schedule.getDosages();
        if (doses == null || doses.isEmpty()) {
            throw new IllegalArgumentException("An automatic schedule must have a dosage");
        }
        for (Map.Entry<Long, Double> entry : doses.entrySet()) {
            Double quantity = entry.getValue();
            if (entry.getKey() == null || quantity == null
                    || !(quantity > 0d)
                    || Double.isNaN(quantity) || Double.isInfinite(quantity)) {
                throw new IllegalArgumentException("Invalid automatic medication dosage");
            }
        }
        if (schedule.getRecur().hasHourlyFrequency()) {
            if (doses.size() != 1 || !doses.containsKey(Schedule.DEFAULT_DOSAGE_KEY)) {
                throw new IllegalArgumentException("Hourly schedules need one default dosage");
            }
        } else if (schedule.getRecur().hasDailyFixedTimes()) {
            if (doses.size() != schedule.getRecur().getDailyFixedTimes().size()) {
                throw new IllegalArgumentException("Fixed-time schedule dosage count differs");
            }
            for (DailyFixedTime time : schedule.getRecur().getDailyFixedTimes()) {
                if (time == null || time.getReference() == null
                        || !doses.containsKey(time.getReference())) {
                    throw new IllegalArgumentException("Missing fixed-time schedule dosage");
                }
            }
        } else {
            // Schedule.getDosage() cannot resolve recurrence rules with
            // neither an hourly default nor fixed time references.
            throw new IllegalArgumentException("Automatic dosage timing is not defined");
        }
    }
}
