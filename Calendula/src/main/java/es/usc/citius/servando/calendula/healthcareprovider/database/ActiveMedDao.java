/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
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
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.healthcareprovider.database;

import com.j256.ormlite.dao.Dao;

import org.joda.time.DateTime;

import java.sql.SQLException;

import es.usc.citius.servando.calendula.database.DatabaseHelper;
import es.usc.citius.servando.calendula.database.GenericDao;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;

public class ActiveMedDao extends GenericDao<ActiveMedEntity, Long> {

    private static final String TAG = "AllergyGroupDao";

    private Dao<ActiveMedEntity, Long> daoInstance = null;

    public ActiveMedDao(DatabaseHelper db) {
        super(db);
    }

    @Override
    public Dao<ActiveMedEntity, Long> getConcreteDao() {
        try {
            if (daoInstance == null)
                daoInstance = dbHelper.getDao(ActiveMedEntity.class);
            return daoInstance;
        } catch (SQLException e) {
            throw new RuntimeException("Error creating patients dao", e);
        }
    }

    public DateTime findLastUpdateDate(Patient patient) {
        try {
            ActiveMedEntity med = dao.queryBuilder()
                    .orderBy(ActiveMedEntity.COLUMN_LAST_UPDATED, false)
                    .where().eq(ActiveMedEntity.COLUMN_PATIENT, patient)
                    .queryForFirst();

            if (med != null) {
                return med.getLastUpdated();
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding models", e);
        }

    }
}
