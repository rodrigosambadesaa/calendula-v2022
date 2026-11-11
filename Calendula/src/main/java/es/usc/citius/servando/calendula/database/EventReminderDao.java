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
        if (eventType == null || dateTime == null) {
            throw new IllegalArgumentException(
                    "Reminder removal requires an event and original time");
        }
        final Long ownerId = patient == null ? null : patient.getId();
        final String ownerCode = patient == null ? null : patient.getCode();
        if (patient != null && (ownerId == null || ownerId <= 0L
                || ownerCode == null || ownerCode.trim().isEmpty())) {
            throw new IllegalArgumentException(
                    "A persisted stable patient identity is required for reminder removal");
        }
        try {
            return com.j256.ormlite.misc.TransactionManager.callInTransaction(
                    dbHelper.getConnectionSource(), () -> {
                        // A caller's cached Patient may refer to a different
                        // owner after a database restore reuses its numeric ID.
                        // Check the stable code *inside* the deletion transaction.
                        if (patient != null) {
                            Patient current = DB.patients().findById(ownerId);
                            if (current == null || !ownerId.equals(current.getId())
                                    || !ownerCode.equals(current.getCode())) {
                                throw new IllegalArgumentException(
                                        "Reminder deletion owner changed");
                            }
                        }
                        QueryBuilder<EventReminder, Long> selection = dao.queryBuilder();
                        // Stable ordering makes a late-delete failure
                        // reproducible and ensures all earlier deletions
                        // are exercised by the enclosing rollback tests.
                        selection.orderBy(EventReminder.COLUMN_ID, true);
                        Where<EventReminder, Long> where = selection.where();
                        where.and(where.eq(EventReminder.COLUMN_EVENT_TYPE, eventType),
                                where.eq(EventReminder.COLUMN_DATE_TIME, dateTime),
                                patient == null
                                        ? where.isNull(EventReminder.COLUMN_PATIENT)
                                        : where.eq(EventReminder.COLUMN_PATIENT, ownerId));
                        int removed = 0;
                        for (EventReminder row : selection.query()) {
                            Patient rowOwner = row.getPatient();
                            if (row.getId() == null || row.getId() <= 0L) {
                                throw new SQLException("Selected reminder has no persisted ID");
                            }
                            if (patient == null) {
                                if (rowOwner != null) {
                                    throw new SQLException("Unassigned deletion selected owned reminder");
                                }
                            } else if (rowOwner == null
                                    || !ownerId.equals(rowOwner.getId())
                                    || !ownerCode.equals(rowOwner.getCode())) {
                                throw new SQLException("Reminder owner changed during deletion");
                            }
                            // Never DELETE a later replacement with a recycled
                            // numeric ID; the UUID belongs to the SQL predicate.
                            if (deleteIfSameInstance(row) != 1) {
                                throw new SQLException("Reminder instance changed during deletion");
                            }
                            removed++;
                        }
                        return removed;
                    });
        } catch (SQLException e) {
            throw new IllegalStateException("Reminder deletion rolled back", e);
        }
    }

    /**
     * Delete exactly the persisted reminder instance that the caller saw.
     * The UUID predicate and numeric ID are evaluated by one SQLite DELETE,
     * not by a vulnerable read-then-delete-by-recycled-ID sequence.
     *
     * The caller owns its transaction/COMMIT and may retire only the captured
     * UUID-bearing Android token after this call reports exactly one row.
     */
    public int deleteIfSameInstance(EventReminder captured) throws SQLException {
        if (captured == null || captured.getId() == null || captured.getId() <= 0L
                || !es.usc.citius.servando.calendula.util.IntentParams
                        .isValidAlarmInstance(captured.getAlarmInstance())) {
            throw new IllegalArgumentException("Valid persisted reminder instance required");
        }
        DeleteBuilder<EventReminder, Long> delete = dao.deleteBuilder();
        Where<EventReminder, Long> where = delete.where();
        where.and(where.eq(EventReminder.COLUMN_ID, captured.getId()),
                where.eq(EventReminder.COLUMN_ALARM_INSTANCE, captured.getAlarmInstance()));
        return delete.delete();
    }

    public int removeOlderThan(DateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("A reminder retention cutoff is required");
        }

        try {
            return com.j256.ormlite.misc.TransactionManager.callInTransaction(
                    dbHelper.getConnectionSource(), () -> {
                        // Keep the coarse time selection in SQL, but inspect
                        // NextTime as a DateTime before deletion. A nested OR
                        // predicate is not reliably translated for nullable
                        // legacy NextTime columns on all supported SQLite/ORM
                        // versions. Deleting by ID also detects partial writes.
                        QueryBuilder<EventReminder, Long> query = dao.queryBuilder();
                        query.where().lt(EventReminder.COLUMN_DATE_TIME, dateTime);
                        int removed = 0;
                        for (EventReminder reminder : query.query()) {
                            if (reminder.getId() == null || reminder.getDateTime() == null) {
                                throw new SQLException("Malformed expired reminder row");
                            }
                            if (reminder.getNextTime() != null
                                    && !reminder.getNextTime().isBefore(dateTime)) {
                                continue; // A postponed reminder is still due.
                            }
                            if (dao.deleteById(reminder.getId()) != 1) {
                                throw new SQLException("Expired reminder delete was incomplete");
                            }
                            removed++;
                        }
                        return removed;
                    });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not prune expired reminders", e);
        }

    }
}
