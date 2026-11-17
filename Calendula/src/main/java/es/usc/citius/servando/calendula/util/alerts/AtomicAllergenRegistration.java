/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import android.content.Context;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.allergies.AllergenFacade;
import es.usc.citius.servando.calendula.allergies.AllergenVO;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;

/**
 * Registers a batch of allergens and reconciles all matching HIGH warnings
 * in ONE outer SQLite transaction. No Android alarm, notification or observer
 * operation executes before the transaction returns successfully.
 *
 * This is a draft clinical-integrity safeguard, not medical-use validation.
 */
public final class AtomicAllergenRegistration {
    private static final String TAG = "AtomicAllergenRegistration";

    private AtomicAllergenRegistration() { }

    /** The SQL batch did commit; repeating it would create duplicate records. */
    public static final class PostCommitRecoveryException extends RuntimeException {
        PostCommitRecoveryException(RuntimeException cause) {
            super("Allergen changes committed; reminder/observer recovery incomplete", cause);
        }
    }

    public static final class Result {
        private final boolean conflicts;
        private final boolean alertsChanged;
        private final boolean schedulesBlocked;

        private Result(boolean conflicts, boolean alertsChanged, boolean schedulesBlocked) {
            this.conflicts = conflicts;
            this.alertsChanged = alertsChanged;
            this.schedulesBlocked = schedulesBlocked;
        }

        public boolean hasConflicts() {
            return conflicts;
        }
    }

    /** Injection seam for disposable SQLite tests, never a provider bypass in the UI. */
    interface ConflictLookup {
        List<Medicine> find(Context context, AllergenVO allergen) throws Exception;
    }

    public static Result register(Context context, Patient selectedOwner,
                                  List<PatientAllergen> requested) {
        return register(context, selectedOwner, requested,
                AllergenFacade::checkNewMedicineAllergies);
    }

    static Result register(Context context, Patient selectedOwner,
                           List<PatientAllergen> requested, ConflictLookup lookup) {
        if (context == null || !PatientOwnerGuard.hasStableIdentity(selectedOwner)
                || requested == null || requested.isEmpty() || lookup == null) {
            throw new IllegalArgumentException("Persisted owner and nonempty allergen batch required");
        }
        // The helper emits nontransactional AlarmManager/observer effects
        // when its own transaction returns. If it were nested inside a
        // caller's outer SQLite transaction, those effects could run BEFORE
        // the real outer COMMIT (or survive its later ROLLBACK).
        // Refuse an ambient transaction rather than falsely claiming that
        // returning from an ORMLite savepoint made clinical data durable.
        if (DB.helper().getWritableDatabase().inTransaction()) {
            throw new IllegalStateException(
                    "Allergen registration requires the outermost SQLite transaction");
        }
        final Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        final Patient owner = new Patient();
        owner.setId(selectedOwner.getId());
        owner.setCode(selectedOwner.getCode());
        final List<PatientAllergen> pending = new ArrayList<>(requested);
        final Set<String> seen = new HashSet<>();
        // Validate every request before the first write. A second invalid
        // element must never turn a successful first INSERT into partial data.
        for (PatientAllergen candidate : pending) {
            if (candidate == null || candidate.getId() != null
                    || candidate.getType() == null
                    || candidate.getIdentifier() == null
                    || candidate.getIdentifier().trim().isEmpty()
                    || candidate.getName() == null
                    || candidate.getName().trim().isEmpty()
                    || !PatientOwnerGuard.sameStableIdentity(
                            owner, candidate.getPatient())
                    || !seen.add(candidate.getType().name() + "\u0000"
                            + candidate.getIdentifier())) {
                throw new IllegalArgumentException("Incomplete or duplicate allergen registration");
            }
        }

        final Result committed;
        try {
            committed = (Result) DB.transaction(() -> {
                requireLiveActiveOwner(app, owner);
                boolean conflicts = false;
                boolean alertsChanged = false;
                boolean schedulesBlocked = false;
                for (PatientAllergen candidate : pending) {
                    requireLiveActiveOwner(app, owner);
                    if (DB.patientAllergens().create(candidate) != 1
                            || candidate.getId() == null) {
                        throw new SQLException("Allergen INSERT did not affect one row");
                    }

                    AllergenVO allergen = new AllergenVO(candidate);
                    List<Medicine> affected = lookup.find(app, allergen);
                    if (affected == null) {
                        throw new SQLException("Unable to verify medicine/allergen conflicts");
                    }
                    final Set<Long> distinctMedicines = new HashSet<>();
                    for (Medicine matched : affected) {
                        if (matched == null || matched.getId() == null
                                || matched.getId() <= 0L
                                || !distinctMedicines.add(matched.getId())
                                || !PatientOwnerGuard.sameStableIdentity(
                                        owner, matched.getPatient())) {
                            throw new SQLException("Ambiguous or foreign medicine in allergy check");
                        }
                        Medicine medicine = new Medicine();
                        medicine.setId(matched.getId());
                        if (DB.medicines().refresh(medicine) != 1
                                || !PatientOwnerGuard.sameStableIdentity(
                                        owner, medicine.getPatient())) {
                            throw new SQLException("Persisted medicine owner changed during allergen registration");
                        }
                        List<PatientAlert> warnings = DB.alerts()
                                .findByPatientMedicineAndType(owner, medicine,
                                        AllergyPatientAlert.class.getCanonicalName());
                        if (warnings.size() > 1) {
                            throw new SQLException("Multiple HIGH allergy warnings require clinical review");
                        }
                        conflicts = true;
                        if (warnings.isEmpty()) {
                            AllergyPatientAlert warning = new AllergyPatientAlert(
                                    medicine, java.util.Collections.singletonList(allergen));
                            if (DB.alerts().create(warning) != 1) {
                                throw new SQLException("HIGH warning INSERT failed");
                            }
                            alertsChanged = true;
                        } else {
                            PatientAlert current = warnings.get(0);
                            if (current.id() == null || current.id() <= 0L
                                    || current.getLevel() != PatientAlert.Level.HIGH
                                    || !PatientOwnerGuard.sameStableIdentity(
                                            owner, current.getPatient())) {
                                throw new SQLException("Existing allergy warning is unverifiable");
                            }
                            AllergyPatientAlert warning = (AllergyPatientAlert) current.map();
                            AllergyPatientAlert.AllergyAlertInfo details = warning.getDetails();
                            if (details == null || details.getAllergens() == null
                                    || details.getAllergens().isEmpty()
                                    || details.getAllergens().contains(null)) {
                                // Missing details never prove a medication safe.
                                throw new SQLException("Existing HIGH allergy details are incomplete");
                            }
                            List<AllergenVO> merged =
                                    new ArrayList<>(details.getAllergens());
                            Set<AllergenVO> uniqueExisting = new HashSet<>();
                            for (AllergenVO existing : merged) {
                                if (existing.getType() == null || existing.getIdentifier() == null
                                        || existing.getIdentifier().trim().isEmpty()
                                        || !uniqueExisting.add(existing)) {
                                    // A duplicate imported identity is not
                                    // verified clinical detail. Preserve the
                                    // HIGH warning and rollback the new row.
                                    throw new SQLException("Existing HIGH allergy identities are ambiguous");
                                }
                            }
                            if (!merged.contains(allergen)) {
                                merged.add(allergen);
                                details.setAllergens(merged);
                                warning.setDetails(details);
                                if (DB.alerts().update(warning) != 1) {
                                    throw new SQLException("HIGH warning UPDATE failed");
                                }
                                alertsChanged = true;
                            }
                        }
                        // An existing HIGH warning does not prove that every
                        // schedule is still BLOCKED. Restores or later schedule
                        // creation can leave a runnable dose behind. Reapply
                        // the same SQL-only blocking policy for both new and
                        // pre-existing warnings before the outer COMMIT.
                        AlertManager.blockSchedulesForMedicineInSql(medicine);
                        schedulesBlocked = true;
                    }
                }
                requireLiveActiveOwner(app, owner);
                return new Result(conflicts, alertsChanged, schedulesBlocked);
            });
        } catch (RuntimeException failure) {
            // ORMLite-generated Java IDs survive rollback, but their SQLite
            // rows do not. Permit an exact synthetic draft to be retried.
            for (PatientAllergen candidate : pending) {
                candidate.setId(null);
            }
            throw failure;
        }

        // These effects are non-transactional. Run each independently so a
        // failed AlarmManager call cannot suppress the committed HIGH warning
        // in observers. Only the selected owner's alarms may be reconciled.
        RuntimeException afterCommitFailure = null;
        if (committed.schedulesBlocked) {
            try {
                requireCurrentOwnerForPlatformEffects(app, owner);
                Agenda.instance().cleanRemindersForPatient(app, owner);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed allergen batch requires alarm recovery");
                afterCommitFailure = failure;
            }
            try {
                requireCurrentOwnerForPlatformEffects(app, owner);
                CalendulaApp.eventBus().post(PersistenceEvents.SCHEDULE_EVENT);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed allergen schedule observer failed");
                if (afterCommitFailure == null) afterCommitFailure = failure;
            }
        }
        if (committed.alertsChanged) {
            try {
                requireCurrentOwnerForPlatformEffects(app, owner);
                CalendulaApp.eventBus().post(PersistenceEvents.ALERT_EVENT);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed allergen alert observer failed");
                if (afterCommitFailure == null) afterCommitFailure = failure;
            }
        }
        if (afterCommitFailure != null) {
            throw new PostCommitRecoveryException(afterCommitFailure);
        }
        return committed;
    }

    /**
     * Platform alarms and UI events are not covered by the already-committed
     * SQLite transaction. A profile switch during COMMIT must not allow the
     * old asynchronous operation to act in a different active-owner context.
     * Treat this as a post-COMMIT recovery failure, never a rolled-back save.
     */
    static void requireCurrentOwnerForPlatformEffects(Context context, Patient expected) {
        try {
            requireLiveActiveOwner(context, expected);
        } catch (SQLException staleOwner) {
            throw new IllegalStateException(
                    "Committed allergy registration requires patient-scoped recovery",
                    staleOwner);
        }
    }

    private static void requireLiveActiveOwner(Context context, Patient expected)
            throws SQLException {
        if (!PatientOwnerGuard.sameStableIdentity(
                expected, DB.patients().findById(expected.getId()))
                || !PatientOwnerGuard.sameStableIdentity(
                        expected, DB.patients().getActive(context))) {
            throw new SQLException("Allergen registration owner changed");
        }
    }
}
