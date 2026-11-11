/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.database;

import android.content.Context;
import androidx.annotation.Nullable;

import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.List;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.healthcareprovider.notifications.ActiveMedNotification;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

public class PatientDao extends GenericDao<Patient, Long> {


    private static final String TAG = "PatientDao";

    public PatientDao(DatabaseHelper db) {
        super(db);
    }

    @Override
    public Dao<Patient, Long> getConcreteDao() {
        try {
            return dbHelper.getPatientDao();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating patients dao", e);
        }
    }

    @Override
    public void saveAndFireEvent(Patient p) {

        Object event = p.getId() == null ? new PersistenceEvents.UserCreateEvent(p) : new PersistenceEvents.UserUpdateEvent(p);
        save(p);
        CalendulaApp.eventBus().post(event);

    }

    // Manage active patient through preferences

    /**
     * Do not authorize a stale UI profile object solely because its numeric
     * SQLite ID was reassigned after restore. Old offline default profiles
     * had no Code; permit their original local-only behavior until the
     * durable-code migration runs, but never accept an unknown remote owner.
     */
    private static boolean sameSelectedPatient(Patient candidate, Patient persisted) {
        if (candidate == null || persisted == null
                || candidate.getId() == null || candidate.getId() <= 0L
                || !candidate.getId().equals(persisted.getId())) {
            return false;
        }
        String submittedCode = candidate.getCode();
        String persistedCode = persisted.getCode();
        if (submittedCode != null && !submittedCode.trim().isEmpty()) {
            return submittedCode.equals(persistedCode);
        }
        return candidate.isDefault() && persisted.isDefault()
                && (submittedCode == null || submittedCode.trim().isEmpty())
                && (persistedCode == null || persistedCode.trim().isEmpty());
    }

    /**
     * Digest is an identity *binding*, not an authentication credential.
     * Domain separation prevents accidental reuse of an unrelated SHA-256.
     * Never store a raw provider subject in the active-selection preferences.
     */
    static String stablePatientDigest(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("calendula:active-patient:v1:" + code)
                            .getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                hex[2 * i] = Character.forDigit((digest[i] & 0xff) >>> 4, 16);
                hex[2 * i + 1] = Character.forDigit(digest[i] & 0x0f, 16);
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException impossibleOnAndroid) {
            throw new IllegalStateException("SHA-256 unavailable", impossibleOnAndroid);
        }
    }

    /**
     * Serialize in-process writes to the active-owner preference pair.
     * Both selection and erasure must use the same lock so a concurrent
     * patient switch cannot be erased between the comparison and edit.
     */
    private static final Object ACTIVE_SELECTION_WRITE_LOCK = new Object();

    /**
     * A recycled numeric patient ID is not authority to clear a new owner's
     * selection: the saved stable digest must still belong to the erased row.
     */
    static boolean matchesDeletedActiveBinding(
            Patient deleted, long savedId, String savedOwnerDigest) {
        if (deleted == null || deleted.getId() == null || deleted.getId() <= 0L
                || savedId != deleted.getId().longValue()) {
            return false;
        }
        String deletedDigest = stablePatientDigest(deleted.getCode());
        return deletedDigest != null && deletedDigest.equals(savedOwnerDigest);
    }

    private static String selectedOwnerBinding() {
        try {
            return PreferenceUtils.getString(PreferenceKeys.PATIENTS_ACTIVE_OWNER, null);
        } catch (ClassCastException restoredInvalidType) {
            // Non-null sentinel: a malformed orphaned digest is not a clean install.
            return "invalid";
        }
    }

    public boolean isActive(Patient p, Context ctx) {
        // Even a caller bearing the current row's code must not turn an old,
        // unbound numeric selection into authorization for that owner.
        if (p == null || p.getId() == null || p.getId() <= 0L) {
            return false;
        }
        try {
            long selectedId = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1L);
            return selectedId == p.getId().longValue()
                    && sameSelectedPatient(p, getActive(ctx));
        } catch (ClassCastException malformedSelection) {
            return false;
        }
    }

    @Nullable
    public Patient getActive(Context ctx) {
        final long id;
        try {
            id = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1L);
        } catch (ClassCastException malformedSelection) {
            return null;
        }
        final boolean reselectionRequired;
        try {
            reselectionRequired = PreferenceUtils.getBoolean(
                    PreferenceKeys.PATIENTS_RESELECTION_REQUIRED, false);
        } catch (ClassCastException malformedRecoveryMarker) {
            return null;
        }
        if (reselectionRequired) {
            return null;
        }
        if (id == -1L) {
            // A fresh install can still use its local default; an orphaned
            // digest after partial preference restore must not select anyone.
            return selectedOwnerBinding() == null ? getDefault() : null;
        }
        if (id <= 0L) {
            return null;
        }

        String binding = selectedOwnerBinding();
        if (binding == null) {
            // Legacy selections must be explicitly reselected in the profile
            // UI; silently binding a restored numeric ID would be unsafe.
            return null;
        }
        Patient persisted = findById(id);
        if (persisted == null) {
            // Remove both coordinates together so a future recycled ID cannot
            // inherit a dangling preference. Never switch to another owner.
            PreferenceUtils.edit()
                    .remove(PreferenceKeys.PATIENTS_ACTIVE.key())
                    .remove(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key())
                    .putBoolean(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key(), true)
                    .apply();
            return null;
        }
        String liveDigest = stablePatientDigest(persisted.getCode());
        if (liveDigest == null || !liveDigest.equals(binding)) {
            // No fallback or migration by row ID: leave the mismatched state
            // inert until an explicit, persisted-profile selection.
            return null;
        }
        return persisted;
    }

    /**
     * Older offline profiles (including manually added non-default
     * profiles) were created with null Code. Assign stable local-only
     * identities on authoritative reads, so these owners remain selectable,
     * editable and removable after upgrading without relying on SQLite IDs.
     *
     * A conditional UPDATE changes only an unidentified default row; a
     * concurrent initializer or existing authenticated subject is never
     * overwritten. Failure leaves the profile unidentified (fail closed).
     */
    private Patient ensureLocalIdentity(Patient patient) {
        if (patient == null
                || patient.getId() == null || patient.getId() <= 0L
                || (patient.getCode() != null
                    && !patient.getCode().trim().isEmpty())) {
            return patient;
        }
        String localCode = "local-" + UUID.randomUUID().toString();
        try {
            getConcreteDao().updateRaw(
                    "UPDATE Patients SET Code = ? WHERE _id = ? "
                            + "AND (Code IS NULL OR TRIM(Code) = '')",
                    localCode, patient.getId().toString());
            // Return authoritative DB state even if another initializer won.
            // Never return the synthetic code merely because it was generated.
            Patient refreshed = findById(patient.getId());
            return refreshed == null ? null : refreshed;
        } catch (SQLException failure) {
            // Viewing/removing this unidentified legacy profile remains
            // blocked instead of pretending an unsuccessful write succeeded.
            es.usc.citius.servando.calendula.util.LogUtil.w(
                    TAG, "Could not install stable local patient identity");
            return patient;
        }
    }

    /**
     * Canonical local profile for trusted UI selection/editor routes.
     * Unidentified existing rows acquire a durable local identity before
     * the UI retains a Patient object. No caller-supplied code is trusted.
     */
    public Patient findByIdWithLocalIdentity(Long id) {
        if (id == null || id <= 0L) {
            return null;
        }
        return ensureLocalIdentity(findById(id));
    }

    @Nullable
    public Patient getDefault() {
        return ensureLocalIdentity(findOneBy(Patient.COLUMN_DEFAULT, true));
    }

    /**
     * Marks a {@link Patient} as being active in the application.
     *
     * @param patient the patient
     */
    public void setActive(Patient patient) {
        // Avoid persisting references to unsaved/deleted users. Revalidate
        // against SQLite rather than trusting an object from stale UI state.
        final Long id = patient == null ? null : patient.getId();
        if (id == null) {
            throw new IllegalArgumentException("Cannot activate a missing patient");
        }
        Patient persisted = findById(id);
        if (!sameSelectedPatient(patient, persisted)) {
            throw new IllegalArgumentException("Cannot activate a stale or mismatched patient");
        }
        String ownerDigest = stablePatientDigest(persisted.getCode());
        if (ownerDigest == null) {
            throw new IllegalArgumentException("Cannot activate an unidentified patient");
        }
        // Existing posted provider notifications may expose an old owner's
        // medication even when SQLite reuses the same numeric patient ID.
        final long previousId;
        final String previousBinding;
        synchronized (ACTIVE_SELECTION_WRITE_LOCK) {
            long savedId;
            try {
                savedId = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1L);
            } catch (ClassCastException invalidPreference) {
                savedId = -1L;
            }
            previousId = savedId;
            previousBinding = selectedOwnerBinding();
            PreferenceUtils.edit()
                    .putLong(PreferenceKeys.PATIENTS_ACTIVE.key(), id)
                    .putString(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key(), ownerDigest)
                    .remove(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key())
                    .apply();
        }
        if (previousId != id || !ownerDigest.equals(previousBinding)) {
            Context application = CalendulaApp.getContext();
            if (application != null) {
                try {
                    ActiveMedNotification.cancel(application);
                } catch (RuntimeException notificationUnavailable) {
                    // Profile activation and its model events must still
                    // complete if NotificationManager is unavailable.
                }
            }
        }
        // A stale UI model may carry outdated attributes even if the ID still
        // exists. Notify listeners with SQLite's authoritative patient record.
        CalendulaApp.eventBus().post(new PersistenceEvents.ActiveUserChangeEvent(persisted));
    }

    /**
     * @see #setActive(Patient)
     */
    public void setActiveById(Long id) {
        // A bare numeric ID can never explicitly select a NEW profile: a
        // database restore can recycle that ID for another patient. This
        // compatibility entry point may only refresh an ALREADY verified
        // binding. New selections must use setActive(Patient) with a stable
        // owner snapshot obtained from the profile chooser.
        if (id == null || id <= 0L) {
            throw new IllegalArgumentException("Cannot activate a missing patient");
        }
        Patient persisted = findById(id);
        if (persisted == null) {
            throw new IllegalArgumentException("Cannot activate a deleted patient");
        }
        String binding = selectedOwnerBinding();
        final long savedId;
        try {
            savedId = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1L);
        } catch (ClassCastException invalidSavedSelection) {
            throw new IllegalArgumentException("Cannot activate an invalid saved selection",
                    invalidSavedSelection);
        }
        final boolean recoveryRequired;
        try {
            recoveryRequired = PreferenceUtils.getBoolean(
                    PreferenceKeys.PATIENTS_RESELECTION_REQUIRED, false);
        } catch (ClassCastException invalidRecoveryMarker) {
            throw new IllegalArgumentException("Saved patient recovery marker is invalid",
                    invalidRecoveryMarker);
        }
        String currentDigest = stablePatientDigest(persisted.getCode());
        if (recoveryRequired || savedId != id.longValue() || currentDigest == null
                || !currentDigest.equals(binding)) {
            throw new IllegalArgumentException("Numeric ID lacks a verified active-owner binding");
        }
        setActive(persisted);
    }


    private Patient requirePersistedPatientForErasure(Patient candidate) {
        Long id = candidate == null ? null : candidate.getId();
        if (id == null || id <= 0L) {
            throw new IllegalArgumentException("Patient erasure requires a positive persisted owner");
        }
        Patient live = findById(id);
        if (live == null) {
            throw new IllegalArgumentException("Cannot erase an already deleted patient");
        }
        // A restored/imported database may reuse the numeric ID of an
        // already-deleted profile. A stale UI object must never erase the
        // replacement patient's medicines, allergies or intake history.
        // Unidentified legacy objects cannot authorize destructive erasure.
        String expectedCode = candidate.getCode();
        if (expectedCode == null || expectedCode.trim().isEmpty()
                || !expectedCode.equals(live.getCode())) {
            throw new IllegalArgumentException("Patient identity changed before erasure");
        }
        return live;
    }

    /**
     * Erase one verified owner's entire clinical graph atomically. Every
     * database write below participates in the SAME outer transaction.
     * AlarmManager, notifications and EventBus cannot roll back with SQLite:
     * those operations must run only after the outer COMMIT succeeds.
     */
    @SuppressWarnings("unchecked")
    public void removeCascade(Patient candidate) {
        final Patient original = requirePersistedPatientForErasure(candidate);
        final Long ownerId = original.getId();
        final List<EventReminder> retiredReminders = (List<EventReminder>) DB.transaction(() -> {
            // Recheck the stable identity inside the SQL transaction. A stale
            // object must never authorize another owner under a reused row ID.
            Patient current = requirePersistedPatientForErasure(candidate);
            if (!ownerId.equals(current.getId())) {
                throw new SQLException("Patient identity changed during erasure");
            }
            removeAllStuffSql(current);
            DB.eventInstances().removeAllForPatient(current);

            // Snapshot the exact OS reminder tokens while rows still exist.
            // Delete only named patient rows; checked counts force a full
            // rollback on a failed/partial SQL statement.
            List<EventReminder> owned = DB.eventReminders().findBy(
                    EventReminder.COLUMN_PATIENT, ownerId);
            for (EventReminder reminder : owned) {
                if (reminder == null || reminder.getId() == null
                        || reminder.getPatient() == null
                        || !ownerId.equals(reminder.getPatient().getId())) {
                    throw new SQLException("Patient reminder owner mismatch");
                }
                // The old patient snapshot and immutable alarm instance
                // must match in the same SQL DELETE, even during a nested
                // transaction or a restored SQLite numeric-ID collision.
                if (DB.eventReminders().deleteIfSameInstance(reminder) != 1) {
                    throw new SQLException("Could not remove patient reminder");
                }
            }
            if (DB.patients().delete(current) != 1) {
                throw new SQLException("Could not remove persisted patient");
            }
            return owned;
        });

        // On interruption after COMMIT, SQLite rows are already absent and
        // stale PendingIntents cannot authorize delivery. Cleanup is best
        // effort; it must not retroactively report an SQL rollback.
        es.usc.citius.servando.calendula.scheduling.Agenda.instance()
                .cancelDeletedPatientReminderTokens(CalendulaApp.getContext(), retiredReminders);
        // Only after COMMIT: clear the deleted owner's cached selection.
        // Never erase a replacement owner's preference after SQLite recycles
        // the numeric patient ID, including a concurrent in-process switch.
        synchronized (ACTIVE_SELECTION_WRITE_LOCK) {
            final String currentBinding = selectedOwnerBinding();
            long currentId;
            try {
                currentId = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1L);
            } catch (ClassCastException invalidSavedId) {
                // Malformed numeric preferences cannot authorize clinical
                // access. Only retire them if the stable binding still points
                // to this specific erased owner.
                currentId = original.getId().longValue();
            }
            if (matchesDeletedActiveBinding(original, currentId, currentBinding)) {
                PreferenceUtils.edit()
                        .remove(PreferenceKeys.PATIENTS_ACTIVE.key())
                        .remove(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key())
                        .putBoolean(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key(), true)
                        .apply();
            }
        }
        DB.medicines().fireEvent();
    }

    /** SQL-only graph deletion used exclusively by the enclosing erasure. */
    private void removeAllStuffSql(Patient patient) throws SQLException {
        final Patient owner = requirePersistedPatientForErasure(patient);
        final Long ownerId = owner.getId();
        for (Medicine medicine : DB.medicines().findAll(ownerId)) {
            if (medicine == null || !PatientOwnerGuard.sameStableIdentity(
                    owner, medicine.getPatient())) {
                throw new SQLException("Patient medicine owner mismatch");
            }
            DB.medicines().deleteCascadeSql(medicine);
        }
        for (Routine routine : DB.routines().findAll(ownerId)) {
            if (routine == null || !PatientOwnerGuard.sameStableIdentity(
                    owner, routine.getPatient())
                    || DB.routines().delete(routine) != 1) {
                throw new SQLException("Could not remove patient routine");
            }
        }
        for (PatientAllergen allergen : DB.patientAllergens().findAll(ownerId)) {
            if (allergen == null || !PatientOwnerGuard.sameStableIdentity(
                    owner, allergen.getPatient())
                    || DB.patientAllergens().delete(allergen) != 1) {
                throw new SQLException("Could not remove patient allergen");
            }
        }
    }

    public void removeAllStuff(Patient p) {
        // Never run a patient-data deletion using an absent or unsaved owner:
        // filtering by NULL would also match unrelated, unassigned records.
        final Long patientId = requirePersistedPatientForErasure(p).getId();

        // Query the owner's rows in SQLite. Comparing boxed Long IDs with ==
        // previously checked object identity, potentially leaving another
        // Long instance with the same value undeleted (notably IDs > 127).
        // Do not scan or mutate another patient's medications or routines.
        for (Medicine m : DB.medicines().findAll(patientId)) {
            // This also removes the medication's schedules, pickups and alerts.
            DB.medicines().deleteCascade(m, true);
        }
        for (Routine r : DB.routines().findAll(patientId)) {
            DB.routines().remove(r);
        }
        // Allergy names and external catalog IDs are patient clinical data.
        // Unlike medicines and routines, they were omitted from the legacy
        // profile deletion path and survived as orphaned records.
        // Match only the persisted owner's numeric key; preserve all other
        // patients' allergies, even when two people share an allergen.
        for (PatientAllergen allergen : DB.patientAllergens().findAll(patientId)) {
            DB.patientAllergens().remove(allergen);
        }
    }
}
