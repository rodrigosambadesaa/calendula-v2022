/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;

/**
 * A stale routine-editor Intent must not expose or mutate a different
 * patient's routine. Only persisted identities are meaningful here.
 */
public final class RoutineOwnerGuard {
    private RoutineOwnerGuard() {
    }

    public static boolean hasActivePatient(Patient patient) {
        return patient != null && patient.getId() != null && patient.getId() > 0L;
    }

    public static boolean isOwnedBy(Routine routine, Patient activePatient) {
        return hasActivePatient(activePatient)
                && routine != null && routine.getId() != null
                && routine.getId() > 0L
                && routine.getPatient() != null
                && routine.getPatient().getId() != null
                && activePatient.getId().equals(routine.getPatient().getId());
    }
}
