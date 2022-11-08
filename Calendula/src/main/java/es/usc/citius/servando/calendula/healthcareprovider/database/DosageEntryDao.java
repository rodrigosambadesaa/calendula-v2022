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
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.healthcareprovider.database;

import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;

import es.usc.citius.servando.calendula.database.DatabaseHelper;
import es.usc.citius.servando.calendula.database.GenericDao;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;

public class DosageEntryDao extends GenericDao<DosageEntryEntity, Long> {

    private static final String TAG = "DosageEntryDao";

    private Dao<DosageEntryEntity, Long> daoInstance = null;

    public DosageEntryDao(DatabaseHelper db) {
        super(db);
    }

    @Override
    public Dao<DosageEntryEntity, Long> getConcreteDao() {
        try {
            if (daoInstance == null)
                daoInstance = dbHelper.getDao(DosageEntryEntity.class);
            return daoInstance;
        } catch (SQLException e) {
            throw new RuntimeException("Error creating DosageEntryEntity dao", e);
        }
    }

}
