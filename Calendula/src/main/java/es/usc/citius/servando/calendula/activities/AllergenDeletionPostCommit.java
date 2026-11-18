/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import java.sql.SQLException;
import java.util.List;

/**
 * Runs independent, patient-scoped recovery operations after an allergen
 * deletion batch has already committed to SQLite.
 *
 * A failed HIGH-warning reconciliation must not skip later allergens or
 * suppress the final medicine observer. It is NEVER safe to report a
 * rollback or invite the user to repeat committed deletions.
 */
final class AllergenDeletionPostCommit {
    private AllergenDeletionPostCommit() {
    }

    interface Cleanup<T> {
        void run(T item) throws SQLException;
    }

    /**
     * @return true only if every cleanup and the optional observer succeeded.
     *         False means the database already committed but needs recovery.
     */
    static <T> boolean reconcile(List<T> committedDeletions, Cleanup<T> cleanup,
                                 Runnable notifyMedicines) {
        if (committedDeletions == null || cleanup == null) {
            throw new IllegalArgumentException("Committed deletions and cleanup are required");
        }
        boolean complete = true;
        for (T deleted : committedDeletions) {
            if (deleted == null) {
                complete = false;
                continue;
            }
            try {
                cleanup.run(deleted);
            } catch (SQLException | RuntimeException failed) {
                // Do not leak patient/allergen identifiers through diagnostics.
                // The calling activity reports committed-but-recovery-incomplete.
                complete = false;
            }
        }
        if (notifyMedicines != null) {
            try {
                notifyMedicines.run();
            } catch (RuntimeException failed) {
                complete = false;
            }
        }
        return complete;
    }
}
