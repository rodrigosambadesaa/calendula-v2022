/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;

/**
 * A schedule editor belongs to the patient who opened it, not to whichever
 * patient happens to be active when the background executor finally runs.
 * Authorization requires both the persisted row ID and the immutable stable
 * owner code. Numeric SQLite IDs alone may be recycled after database restore.
 */
public final class ScheduleSaveOwnerGuard {
    private ScheduleSaveOwnerGuard() {
    }

    public static boolean samePersistedPatient(Patient expected, Patient candidate) {
        return PatientOwnerGuard.sameStableIdentity(expected, candidate);
    }

    public static boolean canShowEditorMedicationList(Patient opened,
                                                       Patient active, Patient persisted) {
        return samePersistedPatient(opened, active)
                && samePersistedPatient(opened, persisted);
    }

    public static boolean canSaveForOpenedPatient(Patient opened, Patient active,
                                                  Patient persisted, Medicine medicine) {
        if (!samePersistedPatient(opened, active)
                || !samePersistedPatient(opened, persisted)
                || medicine == null || medicine.getId() == null
                || medicine.getId() <= 0L) {
            return false;
        }
        // Preserve genuinely shared legacy catalog entries. An assigned
        // medicine may never be attached to another patient's new schedule.
        return medicine.getPatient() == null
                || samePersistedPatient(opened, medicine.getPatient());
    }

    public static boolean canEditSchedule(Patient opened, Schedule inEditor,
                                          Schedule persisted) {
        return persisted != null && persisted.getId() != null
                && persisted.getId() > 0L
                && inEditor != null && inEditor.getId() != null
                && persisted.getId().equals(inEditor.getId())
                && samePersistedPatient(opened, inEditor.getPatient())
                && samePersistedPatient(opened, persisted.getPatient());
    }
}
