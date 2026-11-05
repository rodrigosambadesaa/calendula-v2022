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
import java.util.UUID;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.healthcareprovider.notifications.ActiveMedNotification;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.Routine;
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

    public boolean isActive(Patient p, Context ctx) {
        // An invalid saved owner must never authorize clinical UI actions
        // using a stale caller-supplied Patient object.
        if (p == null || p.getId() == null || p.getId() <= 0L) {
            return false;
        }
        try {
            final long selectedId = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1);
            // A stale preference must not authorize a deleted patient row.
            // Check persisted identity rather than trusting the caller's
            // Patient instance, which can outlive the SQLite record.
            return selectedId == p.getId().longValue()
                    && sameSelectedPatient(p, findById(selectedId));
        } catch (ClassCastException malformedSelection) {
            return false;
        }
    }

    @Nullable
    public Patient getActive(Context ctx) {
        final long id;
        try {
            id = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1);
        } catch (ClassCastException malformedSelection) {
            // A restored String/Boolean is not an active patient identity.
            // Do not silently select a different default profile, persist a
            // replacement or expose its clinical records in this session.
            return null;
        }
        Patient p;
        if (id != -1) {
            p = findById(id);
            if (p == null) {
                p = getDefault();
                if (p != null) {
                    setActive(p);
                } else {
                    // The selected account was removed, and there is no
                    // default to select. Never retain a dangling patient ID
                    // that could be reused by a different SQLite record.
                    PreferenceUtils.edit()
                            .remove(PreferenceKeys.PATIENTS_ACTIVE.key())
                            .apply();
                }
            }
            return ensureLocalIdentity(p);
        } else {
            return getDefault();
        }
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
                            + "AND (Code IS NULL OR TRIM(Code) = '')"
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
        // Existing posted provider update notifications may contain the
        // previous patient's medicine names even when new callbacks are
        // already guarded. Retire only that notification on a real switch.
        long previousId;
        try {
            previousId = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1L);
        } catch (ClassCastException invalidPreference) {
            previousId = -1L;
        }
        PreferenceUtils.edit()
                .putLong(PreferenceKeys.PATIENTS_ACTIVE.key(), id)
                .apply();
        if (previousId != id) {
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
        if (id == null) {
            throw new IllegalArgumentException("Cannot activate a missing patient");
        }
        // Centralize the persisted-record guard and change notification.
        setActive(findById(id));
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

    public void removeCascade(Patient p) {
        // Verify the owner before touching ANY medication, reminders or
        // historical decisions. Missing/deleted/stale ID is not permission to
        // run a destructive partial cleanup.
        Patient persisted = requirePersistedPatientForErasure(p);
        // Removing a schedule retains decided intake history by design.
        // Explicit deletion of a patient must not: scrub their remaining
        // completed/cancelled event snapshots and alarm tokens before removing
        // their identity. The patient is retained if either cleanup fails.
        removeAllStuff(persisted);
        DB.eventInstances().removeAllForPatient(persisted);
        es.usc.citius.servando.calendula.scheduling.Agenda.instance()
                .removeRemindersForPatient(CalendulaApp.getContext(), persisted);
        DB.patients().remove(persisted);

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
