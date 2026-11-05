/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import android.content.Context;
import java.sql.SQLException;
import java.util.UUID;
import es.usc.citius.servando.calendula.DefaultDataGenerator;
import es.usc.citius.servando.calendula.database.DB;

/**
 * Patient profile and requested default routines must be one SQLite commit.
 * Notifications and app event-bus publication happen only after commit.
 */
public final class PatientProfilePersistence {
    private PatientProfilePersistence() {
    }

    public static void save(Patient patient, Context context, boolean initializeMeals) {
        if (patient == null || context == null || patient.getName() == null
                || patient.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("A named patient and context are required");
        }
        final Long originalId = patient.getId();
        final String originalCode = patient.getCode();
        final boolean creating = originalId == null;
        if (creating && (originalCode == null || originalCode.trim().isEmpty())) {
            // Manually added offline patients need the same durable identity
            // as automatic/default profiles. Never infer ownership from a
            // database-generated row ID alone.
            patient.setCode("local-" + UUID.randomUUID().toString());
        }
        if (!creating && originalId <= 0L) {
            throw new IllegalArgumentException("Patient ID is not persisted");
        }
        try {
            DB.transaction(() -> {
                if (creating) {
                    if (DB.patients().create(patient) != 1 || patient.getId() == null) {
                        throw new SQLException("Could not insert patient");
                    }
                } else {
                    // Fail closed on a stale editor after another screen
                    // deleted the profile. Never re-create it by accident.
                    Patient persisted = DB.patients().findById(originalId);
                    // An editor opened before profile deletion or database
                    // restoration must not overwrite a different patient's
                    // profile after its numeric SQLite ID is recycled.
                    if (persisted == null || originalCode == null
                            || originalCode.trim().isEmpty()
                            || !originalCode.equals(persisted.getCode())
                            || DB.patients().update(patient) != 1) {
                        throw new SQLException("Patient identity changed before profile update");
                    }
                }
                if (initializeMeals) {
                    DefaultDataGenerator.generateDefaultRoutines(patient, context);
                }
                return null;
            });
        } catch (RuntimeException failure) {
            // ORMLite leaves the generated ID on Java models after an SQL
            // rollback. Restore the unsaved state so the same editor can
            // safely retry instead of trying to UPDATE a nonexistent row.
            if (creating) {
                patient.setId(null);
                // The same UI draft can safely retry without preserving a
                // generated identity from a rolled-back SQL transaction.
                patient.setCode(originalCode);
            }
            throw failure;
        }
        DB.patients().fireEvent();
    }
}
