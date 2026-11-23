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
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;

/**
 * A generic warning for the active patient's unverifiable medication rows.
 * This never returns a medication name, dosage or another patient's identity.
 */
public final class DailyAgendaReviewPolicy {
    private DailyAgendaReviewPolicy() {
    }

    public static boolean needsReview(EventInstance event, Schedule schedule, Patient active) {
        if (event == null || event.getType() != EventType.MEDICATION_INTAKE
                || !PatientOwnerGuard.hasStableIdentity(active)) {
            return false;
        }
        Patient eventOwner = event.getPatient();
        if (eventOwner != null) {
            // A restored SQLite row ID may be reassigned to a new profile.
            // Even a generic review warning must not disclose the existence
            // of an unrelated person's medications to the active profile.
            if (!PatientOwnerGuard.sameStableIdentity(active, eventOwner)) {
                return false;
            }
        } else {
            // Unassigned event: warn only when the linked schedule's *stable*
            // patient identity agrees with the active selection.
            Patient scheduleOwner = schedule == null ? null : schedule.getPatient();
            if (!PatientOwnerGuard.sameStableIdentity(active, scheduleOwner)) {
                return false;
            }
        }
        return DailyAgendaEventValidator.verifiedDose(event, schedule) == null;
    }
}
