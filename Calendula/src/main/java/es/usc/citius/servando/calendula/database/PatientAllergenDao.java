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
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;

public class PatientAllergenDao extends GenericDao<PatientAllergen, Long> {

    private static final String TAG = "HtmlCacheDAO";

    private Dao<PatientAllergen, Long> daoInstance = null;

    public PatientAllergenDao(DatabaseHelper db) {
        super(db);
    }

    @Override
    public Dao<PatientAllergen, Long> getConcreteDao() {
        try {
            if (daoInstance == null)
                daoInstance = dbHelper.getDao(PatientAllergen.class);
            return daoInstance;
        } catch (SQLException e) {
            throw new RuntimeException("Error creating patients dao", e);
        }
    }

    /**
     * Delete exactly one allergy owned by a previously verified stable
     * patient, in the caller's OUTER SQLite transaction. Do not rely on
     * ORMLite deleteById()'s affected-row count for clinical integrity:
     * disposable API 23/33/37 fixtures reproduced a zero count despite
     * reading the authoritative generated ID. A zero SQL delete must abort
     * the entire group and preserve the original HIGH warnings.
     *
     * The row ID alone is not an authorization boundary. Verify the stable
     * patient fingerprint, allergen type and full identifier, then constrain
     * the actual DELETE by all four persisted coordinates.
     */
    public int deleteVerifiedOwned(PatientAllergen requested, Patient owner)
            throws SQLException {
        final SQLiteDatabase sqlite = dbHelper.getWritableDatabase();
        if (!sqlite.inTransaction()) {
            throw new IllegalStateException(
                    "Verified allergen deletion requires an outer SQLite transaction");
        }
        if (requested == null || requested.getId() == null
                || requested.getId() <= 0L || requested.getType() == null
                || requested.getIdentifier() == null
                || requested.getIdentifier().trim().isEmpty()
                || !PatientOwnerGuard.hasStableIdentity(owner)
                || !PatientOwnerGuard.sameStableIdentity(
                        owner, requested.getPatient())) {
            throw new SQLException("Allergen deletion requires a verified stable identity");
        }
        final Patient currentOwner = DB.patients().findById(owner.getId());
        final PatientAllergen current = findById(requested.getId());
        if (!PatientOwnerGuard.sameStableIdentity(owner, currentOwner)
                || current == null || refresh(current) != 1
                || !PatientOwnerGuard.sameStableIdentity(
                        owner, current.getPatient())
                || current.getType() != requested.getType()
                || !Objects.equals(current.getIdentifier(),
                        requested.getIdentifier())) {
            throw new SQLException("Allergen identity changed before deletion");
        }
        final int removed = sqlite.delete("Allergens",
                PatientAllergen.COLUMN_ID + " = ? AND "
                        + PatientAllergen.COLUMN_PATIENT + " = ? AND "
                        + PatientAllergen.COLUMN_ALLERGEN_TYPE + " = ? AND "
                        + PatientAllergen.COLUMN_IDENTIFIER + " = ?",
                new String[]{String.valueOf(requested.getId()),
                        String.valueOf(owner.getId()), requested.getType().name(),
                        requested.getIdentifier()});
        if (removed != 1) {
            throw new SQLException("Verified allergen deletion did not affect one row");
        }
        return removed;
    }

    public List<PatientAllergen> findAllForActivePatient(Context ctx) {
        return findAll(DB.patients().getActive(ctx));
    }

    public List<PatientAllergen> findAll(Patient p) {
        // No active owner means no patient data; never query NULL-owned rows.
        return p == null || p.getId() == null
                ? Collections.<PatientAllergen>emptyList() : findAll(p.getId());
    }


    public List<PatientAllergen> findAll(Long patientId) {
        if (patientId == null) {
            return Collections.emptyList();
        }
        try {
            return dao.queryBuilder()
                    .where().eq(PatientAllergen.COLUMN_PATIENT, patientId)
                    .query();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding patientAllergens", e);
        }
    }


}
