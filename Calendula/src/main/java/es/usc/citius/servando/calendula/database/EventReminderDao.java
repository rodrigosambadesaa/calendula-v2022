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


    /**
     * Startup and boot alarm recovery only need reminders whose *next* OS
     * delivery lies in the future. Filter in SQLite rather than loading years
     * of historic, handled or malformed reminder rows into Java/ORM memory.
     * This is not a history-deletion operation.
     */
    public List<EventReminder> findFuture(DateTime afterExclusive) {
        if (afterExclusive == null) {
            throw new IllegalArgumentException("A future reminder cutoff is required");
        }
        try {
            QueryBuilder<EventReminder, Long> qb = dao.queryBuilder();
            qb.where().gt(EventReminder.COLUMN_NEXT_TIME, afterExclusive);
            return qb.query();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query pending future reminders", e);
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
                    (p == null ? w.isNull(EventReminder.COLUMN_PATIENT) : w.eq(EventReminder.COLUMN_PATIENT, p))
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
                    (p == null ? w.isNull(EventReminder.COLUMN_PATIENT) : w.eq(EventReminder.COLUMN_PATIENT, p))
            );
            qb.setWhere(w);
            return qb.queryForFirst();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    public int removeBy(Patient patient, EventType eventType, DateTime dateTime) {
        // Destructive reminder cleanup must never interpret a missing type or
        // timestamp as an invitation to target malformed legacy NULL rows.
        // A deliberately null patient is still supported for legacy events.
        if (eventType == null || dateTime == null) {
            throw new IllegalArgumentException("Reminder removal requires an event and original time");
        }
        if (patient != null && (patient.getId() == null || patient.getId() <= 0L
                || DB.patients().findById(patient.getId()) == null)) {
            throw new IllegalArgumentException("Reminder owner is not persisted");
        }

        try {
            if (patient == null) {
                // Nullable foreign keys are supported in lookups, but ORM
                // bulk DELETE WHERE on older SQLite can match zero rows.
                // Remove by persisted row ID under a transaction instead,
                // guaranteeing a named patient's reminders remain untouched.
                return com.j256.ormlite.misc.TransactionManager.callInTransaction(
                        dbHelper.getConnectionSource(), () -> {
                            QueryBuilder<EventReminder, Long> selection = dao.queryBuilder();
                            Where<EventReminder, Long> where = selection.where();
                            where.and(where.eq(EventReminder.COLUMN_EVENT_TYPE, eventType),
                                    where.isNull(EventReminder.COLUMN_PATIENT),
                                    where.eq(EventReminder.COLUMN_DATE_TIME, dateTime));
                            int removed = 0;
                            for (EventReminder row : selection.query()) {
                                if (row.getPatient() != null || row.getId() == null) {
                                    throw new SQLException("Unexpected reminder identity during unassigned delete");
                                }
                                removed += dao.deleteById(row.getId());
                            }
                            return removed;
                        });
            }
            DeleteBuilder<EventReminder, Long> qb = dao.deleteBuilder();
            Where w = qb.where();
            w.and(w.eq(EventReminder.COLUMN_EVENT_TYPE, eventType),
                    (patient == null ? w.isNull(EventReminder.COLUMN_PATIENT) : w.eq(EventReminder.COLUMN_PATIENT, patient)),
                    w.eq(EventReminder.COLUMN_DATE_TIME, dateTime)
            );
            qb.setWhere(w);
            return qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int removeOlderThan(DateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("A reminder retention cutoff is required");
        }

        try {
            DeleteBuilder<EventReminder, Long> qb = dao.deleteBuilder();
            Where<EventReminder, Long> where = qb.where();
            // A delayed intake may have an old ORIGINAL event time but a
            // future NextTime. Pruning by original time alone silently erased
            // the live snooze while its AlarmManager token still existed.
            // Keep any reminder whose next delivery is at/after the cutoff.
            where.and(
                    where.lt(EventReminder.COLUMN_DATE_TIME, dateTime),
                    where.or(
                            where.isNull(EventReminder.COLUMN_NEXT_TIME),
                            where.lt(EventReminder.COLUMN_NEXT_TIME, dateTime)));
            qb.setWhere(where);
            return qb.delete();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding model", e);
        }

    }
}
