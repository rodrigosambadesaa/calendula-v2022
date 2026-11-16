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
import java.util.Objects;
import java.util.Set;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.allergies.AllergenVO;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;
import es.usc.citius.servando.calendula.util.alerts.DrivingAlertHandler;
import es.usc.citius.servando.calendula.util.alerts.StockAlertHandler;

/**
 * SQL-only medication edit and allergy-warning reconciliation. An outer
 * transaction owns EVERY medicine, alert and schedule state mutation.
 * AlarmManager, stock/driving warning providers, recurrence generation and
 * event-bus observers never execute before the SQLite commit.
 *
 * Both creation and editing keep all clinical SQL mutations inside their
 * own transaction. Platform-alarm and observer reconciliation happens only
 * after commit; independent clinical acceptance is still required (#971).
 */
public final class AtomicMedicineAllergyEditor {
    private static final String TAG = "AtomicMedicineAllergyEditor";

    private AtomicMedicineAllergyEditor() {
    }

    public static final class PostCommitRecoveryException extends RuntimeException {
        private PostCommitRecoveryException(RuntimeException cause) {
            super("Medicine changes committed; reminder/observer recovery incomplete", cause);
        }
    }

    private static final class EditResult {
        final Patient owner;
        final List<Schedule> toRegenerate = new ArrayList<>();
        boolean changedAlert;
        boolean changedSchedules;

        EditResult(Patient owner) {
            this.owner = owner;
        }
    }

    /**
     * New-medicine insertion must be one SQL unit with its initial HIGH
     * allergy warning. No nested AlertManager, stock-alert, OS-alarm or
     * event-bus call can run inside the owning transaction.
     */
    public static void create(Context context, Medicine proposed,
                              List<AllergenVO> verifiedAllergens) {
        if (context == null || proposed == null || proposed.getId() != null
                || !PatientOwnerGuard.hasStableIdentity(proposed.getPatient())) {
            throw new IllegalArgumentException("Unsaved medicine with stable owner required");
        }
        if (verifiedAllergens != null && (verifiedAllergens.isEmpty()
                || verifiedAllergens.contains(null))) {
            throw new IllegalArgumentException("Verified allergen set is incomplete");
        }
        final List<AllergenVO> allergens = verifiedAllergens == null
                ? null : new ArrayList<>(verifiedAllergens);
        final Patient owner = new Patient();
        owner.setId(proposed.getPatient().getId());
        owner.setCode(proposed.getPatient().getCode());
        final Context safeContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        final Prescription prescription = proposed.isBoundToPrescription()
                ? DB.drugDB().prescriptions().findByCn(proposed.getCn()) : null;
        // Preserve the original MedicineDao.save() group assignment for new
        // linked prescriptions, without firing its pre-COMMIT callbacks.
        if (prescription != null && prescription.getHomogeneousGroup() != null) {
            proposed.setHomogeneousGroup(prescription.getHomogeneousGroup());
        }

        try {
            DB.transaction(() -> {
                requireCurrentOwner(safeContext, owner);
                if (proposed.getId() != null || DB.medicines().create(proposed) != 1
                        || proposed.getId() == null) {
                    throw new SQLException("Could not atomically insert medicine");
                }
                if (allergens != null) {
                    AllergyPatientAlert warning = new AllergyPatientAlert(proposed, allergens);
                    if (DB.alerts().create(warning) != 1) {
                        throw new SQLException("Could not atomically insert initial allergy warning");
                    }
                }
                requireCurrentOwner(safeContext, owner);
                return null;
            });
        } catch (RuntimeException failure) {
            // ORMLite may leave a generated Java ID after the SQLite INSERT
            // was rolled back. Restore create semantics so the EXACT draft
            // can be retried without updating a nonexistent row.
            proposed.setId(null);
            throw failure;
        }

        RuntimeException error = null;
        if (!currentOwnerMatches(safeContext, owner)) {
            error = new IllegalStateException("Patient changed after medicine creation");
        } else {
            // Try both independent clinical checks: a failure evaluating
            // stock must not silently skip a driving caution and vice versa.
            try {
                StockAlertHandler.checkStockAlerts(proposed);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed medicine stock check needs recovery");
                if (error == null) error = failure;
            }
            try {
                DrivingAlertHandler.checkDrivingAlerts(proposed, prescription);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed medicine driving check needs recovery");
                if (error == null) error = failure;
            }
        }
        try {
            DB.medicines().fireEvent();
        } catch (RuntimeException failure) {
            LogUtil.e(TAG, "Committed medicine list observer needs recovery");
            if (error == null) error = failure;
        }
        try {
            CalendulaApp.eventBus().post(
                    new PersistenceEvents.MedicineAddedEvent(proposed.getId()));
        } catch (RuntimeException failure) {
            LogUtil.e(TAG, "Committed medicine added observer needs recovery");
            if (error == null) error = failure;
        }
        if (allergens != null) {
            try {
                CalendulaApp.eventBus().post(PersistenceEvents.ALERT_EVENT);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed allergy observer needs recovery");
                if (error == null) error = failure;
            }
        }
        if (error != null) {
            throw new PostCommitRecoveryException(error);
        }
    }

    /**
     * @param verifiedAllergens null means the editor has verified that the
     *                          new prescription has no matching allergens.
     *                          A non-null list must be verified and nonempty.
     */
    public static void edit(Context context, Medicine proposed,
                            List<AllergenVO> verifiedAllergens) {
        if (context == null || proposed == null || proposed.getId() == null
                || proposed.getId() <= 0L
                || !PatientOwnerGuard.hasStableIdentity(proposed.getPatient())) {
            throw new IllegalArgumentException("Persisted owned medicine is required");
        }
        if (verifiedAllergens != null && (verifiedAllergens.isEmpty()
                || verifiedAllergens.contains(null))) {
            throw new IllegalArgumentException("Verified allergen set is incomplete");
        }
        final List<AllergenVO> allergens = verifiedAllergens == null
                ? null : new ArrayList<>(verifiedAllergens);
        final Patient owner = new Patient();
        owner.setId(proposed.getPatient().getId());
        owner.setCode(proposed.getPatient().getCode());
        final Context safeContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;

        EditResult committed = (EditResult) DB.transaction(() -> {
            // Check both the selected profile and the persisted row inside
            // the very transaction that will commit the clinical mutations.
            requireCurrentOwner(safeContext, owner);
            // Refresh into a *separate* Java instance. ORMLite may return
            // the editor's own cached object from findById(); refreshing it
            // would otherwise overwrite the user's proposed changes before
            // the SQLite UPDATE has even begun.
            Medicine before = new Medicine();
            before.setId(proposed.getId());
            if (DB.medicines().refresh(before) != 1
                    || !PatientOwnerGuard.sameStableIdentity(
                            owner, before.getPatient())) {
                throw new SQLException("Medicine owner changed before edit");
            }
            boolean changedPrescription =
                    !Objects.equals(before.getCn(), proposed.getCn())
                    || !Objects.equals(before.getDatabase(), proposed.getDatabase());
            List<PatientAlert> warnings = DB.alerts().findByPatientMedicineAndType(
                    owner, before, AllergyPatientAlert.class.getCanonicalName());
            if (warnings.size() > 1) {
                throw new SQLException("Duplicate HIGH allergy alerts require review");
            }
            PatientAlert existing = warnings.isEmpty() ? null : warnings.get(0);
            if (existing != null && (existing.id() == null
                    || existing.getLevel() != PatientAlert.Level.HIGH
                    || !PatientOwnerGuard.sameStableIdentity(
                            owner, existing.getPatient()))) {
                throw new SQLException("Unverifiable HIGH allergy alert");
            }

            // Use the raw DAO operation, not MedicineDao.save(): that wrapper
            // invokes stock/driving alert callbacks before transaction COMMIT.
            if (DB.medicines().update(proposed) != 1) {
                throw new SQLException("Medicine update did not change one row");
            }
            Medicine persisted = new Medicine();
            persisted.setId(proposed.getId());
            if (DB.medicines().refresh(persisted) != 1
                    || !PatientOwnerGuard.sameStableIdentity(
                            owner, persisted.getPatient())) {
                throw new SQLException("Medicine owner changed during edit");
            }

            EditResult result = new EditResult(owner);
            if (allergens != null) {
                if (existing == null) {
                    AllergyPatientAlert warning = new AllergyPatientAlert(persisted, allergens);
                    if (DB.alerts().create(warning) != 1) {
                        throw new SQLException("Cannot persist HIGH allergy warning");
                    }
                    result.changedAlert = true;
                    AlertManager.blockSchedulesForMedicineInSql(persisted);
                    result.changedSchedules = true;
                } else {
                    AllergyPatientAlert warning = (AllergyPatientAlert) existing.map();
                    AllergyPatientAlert.AllergyAlertInfo details = warning.getDetails();
                    if (details == null || details.getAllergens() == null
                            || details.getAllergens().contains(null)) {
                        throw new SQLException("Existing HIGH allergy details are unverifiable");
                    }
                    Set<AllergenVO> incoming = new HashSet<>(allergens);
                    if (!incoming.equals(new HashSet<>(details.getAllergens()))) {
                        details.setAllergens(allergens);
                        warning.setDetails(details);
                        if (DB.alerts().update(warning) != 1) {
                            throw new SQLException("Cannot atomically revise allergy warning");
                        }
                        result.changedAlert = true;
                    }
                }
            } else if (changedPrescription && existing != null) {
                if (DB.alerts().deleteById(existing.id()) != 1) {
                    throw new SQLException("Cannot atomically retire obsolete allergy warning");
                }
                result.changedAlert = true;
                // A different HIGH warning still takes precedence. Do not
                // release any medication while another blocker remains.
                if (DB.alerts().findByMedicineAndLevel(
                        persisted, PatientAlert.Level.HIGH).isEmpty()) {
                    List<Schedule> blocked = DB.schedules().findByMedicineAndState(
                            persisted, Schedule.ScheduleState.BLOCKED);
                    for (Schedule candidate : blocked) {
                        if (!AlertManager.sameStableScheduleMedicine(
                                persisted, candidate)) {
                            throw new SQLException("Blocked schedule has a stale owner");
                        }
                        Schedule stored = DB.schedules().findById(candidate.getId());
                        if (stored == null || DB.schedules().refresh(stored) != 1
                                || !AlertManager.sameStableScheduleMedicine(
                                        persisted, stored)
                                || !stored.hasState(Schedule.ScheduleState.BLOCKED)) {
                            throw new SQLException("Blocked schedule changed before release");
                        }
                        stored.removeState(Schedule.ScheduleState.BLOCKED);
                        stored.addState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY);
                        stored.setRecoveryOwnerDigest(
                                ScheduleAlarmRecovery.ownerDigest(owner));
                        if (DB.schedules().update(stored) != 1) {
                            throw new SQLException("Could not atomically release schedule");
                        }
                        result.toRegenerate.add(stored);
                    }
                    result.changedSchedules = !blocked.isEmpty();
                }
            }
            requireCurrentOwner(safeContext, owner);
            return result;
        });

        // A COMMIT has happened. These effects must never be invoked inside
        // DB.transaction, even when an SQL helper opens nested transactions.
        RuntimeException error = null;
        for (Schedule snapshot : committed.toRegenerate) {
            try {
                if (!currentOwnerMatches(safeContext, committed.owner)) {
                    throw new IllegalStateException("Patient changed after medicine COMMIT");
                }
                ScheduleAlarmRecovery.recoverOne(
                        safeContext, snapshot.getId(), committed.owner);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed medicine schedule requires recovery");
                if (error == null) error = failure;
            }
        }
        // A newly inserted HIGH allergy warning atomically blocks future
        // doses and deletes their SQL occurrences. The OS PendingIntent and
        // displayed notification cannot be revoked *inside* that transaction:
        // if SQL later rolls back, the patient's original dose must survive.
        // Reconcile ONLY the edited patient's tokens after successful COMMIT:
        // this medicine edit must not sweep another patient's reminders or
        // notifications, even if that patient has an obsolete reminder.
        // Revalidating the durable owner also prevents recycled-ID cleanup.
        // The same operation is idempotent for schedules just unblocked by
        // recoverOne; failures remain explicit post-commit recovery failures.
        if (committed.changedSchedules) {
            try {
                Agenda.instance().cleanRemindersForPatient(
                        safeContext, committed.owner);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed allergy schedule needs reminder cleanup");
                if (error == null) error = failure;
            }
        }
        try {
            if (!currentOwnerMatches(safeContext, committed.owner)) {
                throw new IllegalStateException("Patient changed after medicine COMMIT");
            }
            StockAlertHandler.checkStockAlerts(proposed);
        } catch (RuntimeException failure) {
            LogUtil.e(TAG, "Committed medicine stock check requires recovery");
            if (error == null) error = failure;
        }
        try {
            if (!currentOwnerMatches(safeContext, committed.owner)) {
                throw new IllegalStateException("Patient changed after medicine COMMIT");
            }
            Prescription prescription = proposed.isBoundToPrescription()
                    ? DB.drugDB().prescriptions().findByCn(proposed.getCn()) : null;
            DrivingAlertHandler.checkDrivingAlerts(proposed, prescription);
        } catch (RuntimeException failure) {
            LogUtil.e(TAG, "Committed medicine driving check requires recovery");
            if (error == null) error = failure;
        }
        // Notify observers even when post-COMMIT Android work fails: the
        // medicine was already committed, and should never be shown as absent.
        if (committed.changedSchedules) {
            try {
                CalendulaApp.eventBus().post(PersistenceEvents.SCHEDULE_EVENT);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed medicine schedule observer needs recovery");
                if (error == null) error = failure;
            }
        }
        if (committed.changedAlert) {
            try {
                CalendulaApp.eventBus().post(PersistenceEvents.ALERT_EVENT);
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Committed medicine warning observer needs recovery");
                if (error == null) error = failure;
            }
        }
        try {
            PersistenceEvents.ModelCreateOrUpdateEvent event =
                    new PersistenceEvents.ModelCreateOrUpdateEvent(Medicine.class);
            event.model = proposed;
            CalendulaApp.eventBus().post(event);
        } catch (RuntimeException failure) {
            LogUtil.e(TAG, "Committed medicine observer needs recovery");
            if (error == null) error = failure;
        }
        if (error != null) {
            throw new PostCommitRecoveryException(error);
        }
    }

    private static boolean currentOwnerMatches(Context context, Patient expected) {
        return expected != null && PatientOwnerGuard.hasStableIdentity(expected)
                && PatientOwnerGuard.sameStableIdentity(
                        expected, DB.patients().findById(expected.getId()))
                && PatientOwnerGuard.sameStableIdentity(
                        expected, DB.patients().getActive(context));
    }

    private static void requireCurrentOwner(Context context, Patient expected)
            throws SQLException {
        if (!currentOwnerMatches(context, expected)) {
            throw new SQLException("Medicine edit changed active or persisted patient");
        }
    }
}
