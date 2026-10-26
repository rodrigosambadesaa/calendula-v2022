package es.usc.citius.servando.calendula.fragments;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;

/**
 * Never infer a single schedule from an unscoped first-row query.
 * A foreign/ownerless link makes the entire provider card unavailable.
 */
public final class OfficialReminderLinkSelector {
    public enum Action { UNAVAILABLE, CREATE, EDIT, CHOOSE }

    private OfficialReminderLinkSelector() {
    }

    public static final class Selection {
        public final Action action;
        @NonNull public final List<Schedule> schedules;

        private Selection(Action action, List<Schedule> schedules) {
            this.action = action;
            this.schedules = Collections.unmodifiableList(schedules);
        }
    }

    @NonNull
    public static Selection inspect(Long displayedPatientId, Long activePatientId,
                                    Long providerPatientId, List<Schedule> linked) {
        if (displayedPatientId == null || displayedPatientId <= 0
                || !displayedPatientId.equals(activePatientId)
                || !displayedPatientId.equals(providerPatientId)
                || linked == null) {
            return new Selection(Action.UNAVAILABLE, Collections.emptyList());
        }
        List<Schedule> owned = new ArrayList<>();
        for (Schedule schedule : linked) {
            if (schedule == null || schedule.getId() == null || schedule.getId() <= 0
                    || !sameOwner(displayedPatientId, schedule.getPatient())) {
                return new Selection(Action.UNAVAILABLE, Collections.emptyList());
            }
            Medicine medicine = schedule.getMedicine();
            // A missing medicine is not an authorized, editable reminder.
            // Fail closed rather than treating a dangling local link as owned.
            if (medicine == null || medicine.getId() == null || medicine.getId() <= 0L
                    || !sameOwner(displayedPatientId, medicine.getPatient())) {
                return new Selection(Action.UNAVAILABLE, Collections.emptyList());
            }
            owned.add(schedule);
        }
        return new Selection(owned.isEmpty() ? Action.CREATE :
                owned.size() == 1 ? Action.EDIT : Action.CHOOSE, owned);
    }

    private static boolean sameOwner(Long expected, Patient patient) {
        return patient != null && patient.getId() != null
                && expected.equals(patient.getId());
    }
}
