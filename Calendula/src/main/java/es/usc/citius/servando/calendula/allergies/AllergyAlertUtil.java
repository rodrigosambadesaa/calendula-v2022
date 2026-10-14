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

package es.usc.citius.servando.calendula.allergies;

import android.content.Context;

import com.j256.ormlite.stmt.PreparedQuery;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.alerts.AlertManager;


public class AllergyAlertUtil {


    private static final String TAG = "AllergyAlertUtil";


    public static List<PatientAlert> getAlertsForMedicine(final Medicine m) throws SQLException {
        LogUtil.d(TAG, "getAlertsForMedicine: verified lookup started");
        if (m == null || m.getPatient() == null) {
            throw new IllegalArgumentException("Persisted medicine and patient required");
        }
        // Generic field queries only compare IDs. A stale medicine object
        // could hide alerts for a newly assigned owner or expose corrupted
        // cross-patient rows. The scoped DAO refreshes and checks both owners.
        return DB.alerts().findByPatientMedicineAndType(m.getPatient(), m,
                AllergyPatientAlert.class.getCanonicalName());
    }

    /**
     * Removes all allergy alerts for the given medicine.
     *
     * @param m the medicine
     */
    public static void removeAllergyAlerts(final Medicine m) throws SQLException {

        LogUtil.d(TAG, "removeAllergyAlerts: medicine lookup started");
        final List<PatientAlert> alerts = getAlertsForMedicine(m);
        // Allergy alerts are HIGH clinical warnings that block treatment
        // schedules. A raw DAO deletion left those schedules BLOCKED forever.
        // AlertManager validates every persisted owner, deletes atomically and
        // unblocks the schedule only if no other HIGH alert remains.
        // Do not wrap the following in an *outer* transaction: alarm and
        // schedule changes must occur after each committed alert deletion.
        for (PatientAlert alert : alerts) {
            AlertManager.removeAlert(alert);
        }
        LogUtil.d(TAG, "removeAllergyAlerts: Removed " + alerts.size() + " alerts");

    }

    /**
     * Update allergy HIGH alerts safely after a patient's allergen row has
     * been deleted. Separate the SQLite transaction from Android reminder
     * effects: AlertManager must NEVER run inside our outer transaction.
     *
     * Broken legacy warning details are preserved, not treated as proof of
     * absence of a serious allergy.
     */
    public static void removeAllergyAlerts(final Context c, final PatientAllergen allergen)
            throws SQLException {
        if (c == null || allergen == null || allergen.getPatient() == null
                || allergen.getPatient().getId() == null
                || allergen.getPatient().getId() <= 0L
                || allergen.getType() == null || allergen.getIdentifier() == null) {
            throw new IllegalArgumentException("Persisted patient and allergen identity required");
        }
        final Long ownerId = allergen.getPatient().getId();
        if (DB.patients().findById(ownerId) == null) {
            throw new IllegalStateException("Cannot clean allergy alerts for a deleted patient");
        }
        final AllergenVO removedAllergen = new AllergenVO(allergen);
        final List<PatientAlert> toRemove = new ArrayList<>();
        final boolean[] updated = {false};

        DB.transaction(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                final PreparedQuery<PatientAlert> query = DB.alerts().queryBuilder().where()
                        .eq(PatientAlert.COLUMN_TYPE, AllergyPatientAlert.class.getCanonicalName())
                        .and().eq(PatientAlert.COLUMN_PATIENT, ownerId).prepare();
                final List<PatientAlert> alerts = DB.alerts().query(query);
                for (PatientAlert alert : alerts) {
                    if (alert == null || alert.id() == null
                            || alert.getPatient() == null
                            || !ownerId.equals(alert.getPatient().getId())
                            || alert.getMedicine() == null
                            || alert.getMedicine().getId() == null) {
                        throw new SQLException("Allergy alert owner is incomplete");
                    }
                    Medicine stored = DB.medicines().findById(alert.getMedicine().getId());
                    if (stored == null || DB.medicines().refresh(stored) != 1
                            || stored.getPatient() == null
                            || !ownerId.equals(stored.getPatient().getId())) {
                        throw new SQLException("Allergy alert and medicine owners differ");
                    }
                    AllergyPatientAlert mapped = (AllergyPatientAlert) alert.map();
                    final AllergyPatientAlert.AllergyAlertInfo details = mapped.getDetails();
                    if (details == null || details.getAllergens() == null) {
                        // Never remove a HIGH alert whose stored allergen
                        // details are missing or cannot be verified.
                        continue;
                    }
                    List<AllergenVO> remaining = new ArrayList<>(details.getAllergens());
                    if (!remaining.remove(removedAllergen)) {
                        continue;
                    }
                    if (remaining.isEmpty()) {
                        // Defer deletion until after SQL COMMIT so schedule
                        // unblocking and AlarmManager work cannot outlive a
                        // transaction rollback.
                        toRemove.add(alert);
                    } else {
                        details.setAllergens(remaining);
                        mapped.setDetails(details);
                        if (DB.alerts().update(mapped) != 1) {
                            throw new SQLException("Allergy alert details were not updated");
                        }
                        updated[0] = true;
                    }
                }
                return null;
            }
        });
        for (PatientAlert alert : toRemove) {
            AlertManager.removeAlert(alert);
        }
        if (updated[0] || !toRemove.isEmpty()) {
            es.usc.citius.servando.calendula.CalendulaApp.eventBus().post(
                    es.usc.citius.servando.calendula.events.PersistenceEvents.ALERT_EVENT);
        }
    }


    /**
     * Checks if any allergy alerts are present for a given medicine.
     *
     * @param m the medicine
     * @return <code>true</code> if there are any allergy alerts, <code>false</code> otherwise.
     * @throws SQLException
     */
    public static boolean hasAllergyAlerts(final Medicine m) throws SQLException {
        LogUtil.d(TAG, "hasAllergyAlerts: checking medicine");

        return getAlertsForMedicine(m).size() > 0;
    }
}
