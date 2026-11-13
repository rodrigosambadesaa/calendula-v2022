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

    /**
     * A restored database may reuse a clinical patient's numeric row ID.
     * A stale event/selection must therefore also carry the same stable
     * patient code. Neither one nor two unidentified legacy patients can
     * authorize intake/stock changes from a recycled numeric SQLite ID.
     * Local-only profiles must obtain a persisted stable code first.
     */
    public static boolean samePatientIdentity(Patient selected, Patient stored) {
        if (selected == null || stored == null
                || selected.getId() == null || selected.getId() <= 0L
                || !selected.getId().equals(stored.getId())) {
            return false;
        }
        String selectedCode = selected.getCode();
        String storedCode = stored.getCode();
        return selectedCode != null && !selectedCode.trim().isEmpty()
                && storedCode != null && !storedCode.trim().isEmpty()
                && selectedCode.equals(storedCode);
    }

    public static boolean matchesPersistedEvent(EventInstance selected, EventInstance stored) {
        return selected != null && stored != null
                && selected.getId() != null && selected.getId() > 0L
                && selected.getId().equals(stored.getId())
                && selected.getRef() != null && selected.getRef() > 0L
                && selected.getRef().equals(stored.getRef())
                && selected.getTime() != null && selected.getTime().equals(stored.getTime())
                && samePatientIdentity(selected.getPatient(), stored.getPatient())
                && selected.getType() == es.usc.citius.servando.calendula.scheduling.model.EventType.MEDICATION_INTAKE
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
                || event == null
                || !samePatientIdentity(selectedPatient, event.getPatient())
                || !selectedTime.equals(event.getTime())
                || DailyAgendaEventValidator.verifiedDose(event, schedule) == null) {
            return false;
        }
        if (schedule == null) {
            return false;
        }
        // A legacy row can be marked "official" while its imported source
        // identity has been deleted or lost. Such a row must NOT regain
        // confirmation privileges simply because isBoundToActiveMed() is false.
        // Mirror the patient-safe notification gate: official means a
        // verifiable external source is mandatory, not optional.
        if (schedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)
                && !schedule.isBoundToActiveMed()) {
            return false;
        }
        return !schedule.isBoundToActiveMed()
                || DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, importedSource);
    }
}
