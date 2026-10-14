/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.joda.time.DateTime;

import java.util.Objects;

import es.usc.citius.servando.calendula.fragments.DailyAgendaEventValidator;
import es.usc.citius.servando.calendula.fragments.DailyAgendaOfficialTreatmentValidator;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;

/**
 * Confirmation is a clinical action, not just a display. An unknown owner,
 * missing dose, changed event or mismatched imported prescription cannot
 * authorize changing the intake/stock state.
 */
public final class ConfirmIntakeGuard {
    private ConfirmIntakeGuard() {
    }

    public static boolean matchesPersistedEvent(EventInstance selected, EventInstance stored) {
        return selected != null && stored != null
                && selected.getId() != null && selected.getId().equals(stored.getId())
                && Objects.equals(selected.getRef(), stored.getRef())
                && Objects.equals(selected.getTime(), stored.getTime())
                && selected.getPatient() != null && stored.getPatient() != null
                && selected.getPatient().getId() != null
                && selected.getPatient().getId().equals(stored.getPatient().getId())
                && selected.getType() == stored.getType()
                && selected.completed() == stored.completed()
                && selected.cancelled() == stored.cancelled()
                && samePositiveDose(selected, stored);
    }

    private static boolean samePositiveDose(EventInstance selected, EventInstance stored) {
        try {
            double first = selected.getDoubleParam(EventInstance.PARAM_DOSE);
            double second = stored.getDoubleParam(EventInstance.PARAM_DOSE);
            return first > 0.0d && !Double.isNaN(first) && !Double.isInfinite(first)
                    && Double.compare(first, second) == 0;
        } catch (RuntimeException malformed) {
            // An absent or nonnumeric dose cannot authorize a clinical action.
            return false;
        }
    }

    /** Row identity captured on a prior render must still be at this position. */
    public static boolean matchesVisibleRow(java.util.List<EventInstance> shown,
                                            int currentPosition, EventInstance selected) {
        return shown != null && currentPosition >= 0
                && currentPosition < shown.size()
                && matchesPersistedEvent(selected, shown.get(currentPosition));
    }

    public static boolean verified(EventInstance event, Patient selectedPatient,
                                   DateTime selectedTime, Schedule schedule,
                                   ActiveMedEntity importedSource) {
        if (selectedPatient == null || selectedPatient.getId() == null
                || selectedPatient.getId() <= 0L || selectedTime == null
                || event == null || event.getPatient() == null
                || !selectedPatient.getId().equals(event.getPatient().getId())
                || !selectedTime.equals(event.getTime())
                || DailyAgendaEventValidator.verifiedDose(event, schedule) == null) {
            return false;
        }
        return schedule == null || !schedule.isBoundToActiveMed()
                || DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, importedSource);
    }
}
