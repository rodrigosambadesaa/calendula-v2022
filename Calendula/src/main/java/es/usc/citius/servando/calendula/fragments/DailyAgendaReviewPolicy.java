/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

/**
 * A generic warning for the active patient's unverifiable medication rows.
 * This never returns a medication name, dosage or another patient's identity.
 */
public final class DailyAgendaReviewPolicy {
    private DailyAgendaReviewPolicy() {
    }

    public static boolean needsReview(EventInstance event, Schedule schedule, Patient active) {
        if (event == null || event.getType() != EventType.MEDICATION_INTAKE
                || active == null || active.getId() == null) {
            return false;
        }
        Patient eventOwner = event.getPatient();
        if (eventOwner != null && eventOwner.getId() != null) {
            // A cross-patient schedule reference is an issue for the event's
            // actual owner, not an excuse to reveal that event to another user.
            if (!active.getId().equals(eventOwner.getId())) {
                return false;
            }
        } else {
            // Unknown event owner: only warn if the referenced schedule is
            // explicitly owned by the currently active patient.
            Patient scheduleOwner = schedule == null ? null : schedule.getPatient();
            if (scheduleOwner == null || scheduleOwner.getId() == null
                    || !active.getId().equals(scheduleOwner.getId())) {
                return false;
            }
        }
        return DailyAgendaEventValidator.verifiedDose(event, schedule) == null;
    }
}
