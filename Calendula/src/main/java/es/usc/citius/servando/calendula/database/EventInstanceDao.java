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

import android.os.Bundle;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;

import org.joda.time.DateTime;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

public class EventInstanceDao extends GenericDao<EventInstance, Long> {

    private static final String TAG = "EventInstanceDao";

    public EventInstanceDao(DatabaseHelper db) {
        super(db);
    }


    @Override
    public Dao<EventInstance, Long> getConcreteDao() {
        try {
            return dbHelper.getEventInstanceDao();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating medicines dao", e);
        }
    }

    @Override
    public void fireEvent() {
        CalendulaApp.eventBus().post(PersistenceEvents.EVENT_INSTANCE_EVENT);
    }

    public void saveAll(Collection<EventInstance> evts) {
        for (EventInstance e : evts) {
            save(e);
        }
    }

    public List<EventInstance> findByType(EventType type) {
        try {
            QueryBuilder<EventInstance, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.eq(EventInstance.COLUMN_EVENT_TYPE, type);
            qb.setWhere(w);
            return qb.query();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public List<EventInstance> findByTypeAndTime(EventType type, DateTime dateTime) {
        try {
            QueryBuilder<EventInstance, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type), w.eq(EventInstance.COLUMN_DATE_TIME, dateTime));
            qb.setWhere(w);
            return qb.query();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public List<EventInstance> find(EventType type, DateTime dateTime, Patient p) {
        try {
            QueryBuilder<EventInstance, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventInstance.COLUMN_PATIENT, p)
            );
            qb.setWhere(w);
            return qb.query();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public boolean exists(EventType type, DateTime dateTime, Long ref) {
        try {
            QueryBuilder<EventInstance, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventInstance.COLUMN_REF, ref)
            );
            qb.setWhere(w);
            return qb.countOf() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public boolean exists(EventType type, DateTime dateTime, Patient p, boolean completed) {
        try {
            QueryBuilder<EventInstance, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventInstance.COLUMN_PATIENT, p),
                    w.eq(EventInstance.COLUMN_COMPLETED, completed)
            );
            qb.setWhere(w);
            return qb.countOf() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public boolean exists(EventType type, DateTime dateTime) {
        try {
            QueryBuilder<EventInstance, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime)
            );
            qb.setWhere(w);
            return qb.countOf() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public int removeDistinctTime(EventType type, Long ref, Collection<DateTime> timesToPreserve, DateTime from) {
        try {
            DeleteBuilder<EventInstance, Long> qb = dao.deleteBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_REF, ref),
                    w.notIn(EventInstance.COLUMN_DATE_TIME, timesToPreserve),
                    w.gt(EventInstance.COLUMN_DATE_TIME,from)
            );
            qb.setWhere(w);
            return qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }

    }

    public int removeByRef(Long ref) {
        try {
            DeleteBuilder<EventInstance, Long> qb = dao.deleteBuilder();
            Where w = qb.where();
            w.eq(EventInstance.COLUMN_REF, ref);
            qb.setWhere(w);
            return qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public int removeByRefAndTime(Long ref, DateTime startTime) {
        try {
            DeleteBuilder<EventInstance, Long> qb = dao.deleteBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_REF, ref),
                  w.gt(EventInstance.COLUMN_DATE_TIME, startTime)  );
            qb.setWhere(w);
            return qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public int checkAll(EventType type, DateTime dateTime, Patient p, DateTime completedAt) {
        try {
            UpdateBuilder<EventInstance, Long> qb = dao.updateBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventInstance.COLUMN_PATIENT, p),
                    w.eq(EventInstance.COLUMN_COMPLETED, false)
            );
            qb.updateColumnValue(EventInstance.COLUMN_COMPLETED, true);
            qb.updateColumnValue(EventInstance.COLUMN_COMPLETED_DATETIME, completedAt);
            qb.setWhere(w);
            return qb.update();
        } catch (SQLException e) {

            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public int cancelUncompleted(EventType type, DateTime dateTime, Patient p, DateTime completedAt) {
        try {
            UpdateBuilder<EventInstance, Long> qb = dao.updateBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventInstance.COLUMN_PATIENT, p),
                    w.eq(EventInstance.COLUMN_COMPLETED, false)
            );
            qb.updateColumnValue(EventInstance.COLUMN_CANCELLED, true);
            qb.updateColumnValue(EventInstance.COLUMN_COMPLETED_DATETIME, completedAt);
            qb.setWhere(w);
            return qb.update();
        } catch (SQLException e) {

            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public int confirm(EventType type, DateTime dateTime, Patient p, DateTime completedAt) {
        try {
            UpdateBuilder<EventInstance, Long> qb = dao.updateBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventInstance.COLUMN_PATIENT, p),
                    w.eq(EventInstance.COLUMN_COMPLETED, false)
            );
            qb.updateColumnValue(EventInstance.COLUMN_COMPLETED, true);
            qb.updateColumnValue(EventInstance.COLUMN_CANCELLED, false);
            qb.updateColumnValue(EventInstance.COLUMN_COMPLETED_DATETIME, completedAt);
            qb.setWhere(w);
            return qb.update();
        } catch (SQLException e) {

            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public int updateParams(EventType type, DateTime dateTime, Long ref, Bundle params) {
        try {
            UpdateBuilder<EventInstance, Long> qb = dao.updateBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventInstance.COLUMN_REF, ref)
            );
            qb.updateColumnValue(EventInstance.COLUMN_PARAMS, params);
            qb.setWhere(w);
            return qb.update();
        } catch (SQLException e) {

            throw new RuntimeException("Error updating event params", e);
        }
    }

    public boolean existBetween(EventType type, DateTime start, DateTime end) {

        try {
            QueryBuilder<EventInstance, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(  w.ge(EventInstance.COLUMN_DATE_TIME, start),
                    w.lt(EventInstance.COLUMN_DATE_TIME, end),
                    w.eq(EventInstance.COLUMN_EVENT_TYPE, type)
            );
            qb.setWhere(w);
            return qb.countOf() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public void removeAll(EventType type) {
        try {
            DeleteBuilder<EventInstance, Long> qb = dao.deleteBuilder();
            qb.setWhere(qb.where().eq(EventInstance.COLUMN_EVENT_TYPE, type));
            qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }


    public int removeOlderThan(EventType type, DateTime dateTime) {
        try {
            DeleteBuilder<EventInstance, Long> qb = dao.deleteBuilder();
            Where w = qb.where();
            w.and( w.lt(EventInstance.COLUMN_DATE_TIME, dateTime),
                   w.eq(EventInstance.COLUMN_EVENT_TYPE, type)
            );
            qb.setWhere(w);
            return qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding model", e);
        }
    }

    public int removeBeyond(EventType type, DateTime dateTime) {
        try {
            DeleteBuilder<EventInstance, Long> qb = dao.deleteBuilder();
            Where w = qb.where();
            w.and( w.gt(EventInstance.COLUMN_DATE_TIME, dateTime),
                    w.eq(EventInstance.COLUMN_EVENT_TYPE, type)
            );
            qb.setWhere(w);
            return qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding model", e);
        }
    }
}
