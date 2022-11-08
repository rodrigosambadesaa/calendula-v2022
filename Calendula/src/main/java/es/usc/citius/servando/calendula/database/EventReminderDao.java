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

package es.usc.citius.servando.calendula.database;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

import org.joda.time.DateTime;

import java.sql.SQLException;
import java.util.List;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

public class EventReminderDao extends GenericDao<EventReminder, Long> {

    private static final String TAG = "EventReminderDao";

    public EventReminderDao(DatabaseHelper db) {
        super(db);
    }


    @Override
    public Dao<EventReminder, Long> getConcreteDao() {
        try {
            return dbHelper.getEventReminderDao();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating medicines dao", e);
        }
    }


    public List<EventReminder> findByType(EventType type) {
        try {
            QueryBuilder<EventReminder, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.eq(EventReminder.COLUMN_EVENT_TYPE, type);
            qb.setWhere(w);
            return qb.query();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public boolean exists(EventType type, DateTime dateTime, Patient p) {
        try {
            QueryBuilder<EventReminder, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(EventReminder.COLUMN_EVENT_TYPE, type),
                    w.eq(EventReminder.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventReminder.COLUMN_PATIENT, p)
            );
            qb.setWhere(w);
            return qb.countOf() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public EventReminder findBy(EventType type, DateTime dateTime, Patient p) {
        try {
            QueryBuilder<EventReminder, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(EventReminder.COLUMN_EVENT_TYPE, type),
                    w.eq(EventReminder.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventReminder.COLUMN_PATIENT, p)
            );
            qb.setWhere(w);
            return qb.queryForFirst();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public int removeBy(Patient patient, EventType eventType, DateTime dateTime) {
        try {
            DeleteBuilder<EventReminder, Long> qb = dao.deleteBuilder();
            Where w = qb.where();
            w.and(w.eq(EventReminder.COLUMN_EVENT_TYPE, eventType),
                    w.eq(EventReminder.COLUMN_PATIENT, patient),
                    w.eq(EventReminder.COLUMN_DATE_TIME, dateTime)
            );
            qb.setWhere(w);
            return qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int removeOlderThan(DateTime dateTime) {
        try {
            DeleteBuilder<EventReminder, Long> qb = dao.deleteBuilder();
            Where w = qb.where();
            qb.setWhere(w.lt(EventInstance.COLUMN_DATE_TIME, dateTime));
            return qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding model", e);
        }

    }
}
