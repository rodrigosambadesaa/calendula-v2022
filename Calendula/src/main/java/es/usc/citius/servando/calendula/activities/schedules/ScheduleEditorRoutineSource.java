/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities.schedules;

import java.util.Collections;
import java.util.List;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.util.ScheduleSaveOwnerGuard;

/** Revalidates the opened editor's patient before loading any private routine. */
final class ScheduleEditorRoutineSource {
    private ScheduleEditorRoutineSource() {
    }

    static Patient authorizedPatient(ScheduleEditorOwnerState.OwnerBinding opened,
                                     Patient active) {
        if (!ScheduleEditorOwnerState.stillOwnedBy(opened, active)) {
            return null;
        }
        Patient persisted = DB.patients().findById(opened.id);
        return ScheduleEditorOwnerState.stillOwnedBy(opened, persisted)
                && ScheduleSaveOwnerGuard.canShowEditorMedicationList(
                        persisted, active, persisted) ? persisted : null;
    }

    static List<Routine> load(ScheduleEditorOwnerState.OwnerBinding opened, Patient active) {
        Patient owner = authorizedPatient(opened, active);
        return owner == null ? Collections.emptyList() : DB.routines().findAll(owner.getId());
    }
}
