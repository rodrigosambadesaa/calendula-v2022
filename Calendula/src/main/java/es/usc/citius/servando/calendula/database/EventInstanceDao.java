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
                    (p == null ? w.isNull(EventInstance.COLUMN_PATIENT) : w.eq(EventInstance.COLUMN_PATIENT, p))
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
                    (p == null ? w.isNull(EventInstance.COLUMN_PATIENT) : w.eq(EventInstance.COLUMN_PATIENT, p)),
                    w.eq(EventInstance.COLUMN_COMPLETED, completed)
            );
            qb.setWhere(w);
            return qb.countOf() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding event instances", e);
        }
    }

    /**
     * Build the same patient-scoped, active-intake predicate for both the
     * delivery authorization and the dose list displayed in notifications.
     */
    private QueryBuilder<EventInstance, Long> pendingQuery(
            EventType type, DateTime dateTime, Patient patient) throws SQLException {
        QueryBuilder<EventInstance, Long> qb = dao.queryBuilder();
        Where<EventInstance, Long> w = qb.where();
        w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                (patient == null ? w.isNull(EventInstance.COLUMN_PATIENT)
                        : w.eq(EventInstance.COLUMN_PATIENT, patient)),
                w.eq(EventInstance.COLUMN_COMPLETED, false),
                w.eq(EventInstance.COLUMN_CANCELLED, false));
        qb.setWhere(w);
        return qb;
    }

    /** Only currently actionable intake events may authorize a reminder. */
    public boolean existsPending(EventType type, DateTime dateTime, Patient patient) {
        try {
            return pendingQuery(type, dateTime, patient).countOf() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error finding pending event instances", e);
        }
    }

    /** Only actionable doses belonging to this patient appear in notifications. */
    public List<EventInstance> findPending(EventType type, DateTime dateTime, Patient patient) {
        try {
            return pendingQuery(type, dateTime, patient).query();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding pending medication events", e);
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

    /**
     * A schedule revision may remove obsolete *pending* future occurrences,
     * but must never erase an already completed/cancelled intake decision.
     * Empty time filters are valid (a recurrence can have no remaining times).
     */
    public int removeDistinctTime(EventType type, Long ref,
                                  Collection<DateTime> timesToPreserve, DateTime from) {
        try {
            return com.j256.ormlite.misc.TransactionManager.callInTransaction(
                    dbHelper.getConnectionSource(), () -> {
                        QueryBuilder<EventInstance, Long> query = dao.queryBuilder();
                        Where<EventInstance, Long> where = query.where();
                        if (timesToPreserve == null || timesToPreserve.isEmpty()) {
                            where.and(where.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                                    where.eq(EventInstance.COLUMN_REF, ref),
                                    where.gt(EventInstance.COLUMN_DATE_TIME, from));
                        } else {
                            where.and(where.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                                    where.eq(EventInstance.COLUMN_REF, ref),
                                    where.notIn(EventInstance.COLUMN_DATE_TIME, timesToPreserve),
                                    where.gt(EventInstance.COLUMN_DATE_TIME, from));
                        }
                        query.setWhere(where);
                        int removed = 0;
                        for (EventInstance event : query.query()) {
                            if (event.getId() == null) {
                                throw new SQLException("Future schedule event has no persisted ID");
                            }
                            if (event.completed() || event.cancelled()) {
                                // Preserve historical decisions, including
                                // doses taken early or cancelled for tomorrow.
                                continue;
                            }
                            if (dao.deleteById(event.getId()) != 1) {
                                throw new SQLException("Expected one obsolete pending event to be removed");
                            }
                            removed++;
                        }
                        return removed;
                    });
        } catch (SQLException e) {
            throw new RuntimeException("Error removing obsolete pending event instances", e);
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

    /**
     * Blocking an active medication or prescription removes unhandled future
     * occurrences, not decisions already recorded for those times.
     * Preserve completion/cancellation history while cleaning pending events.
     */
    public int removeByRefAndTime(Long ref, DateTime startTime) {
        try {
            return com.j256.ormlite.misc.TransactionManager.callInTransaction(
                    dbHelper.getConnectionSource(), () -> {
                        QueryBuilder<EventInstance, Long> query = dao.queryBuilder();
                        Where<EventInstance, Long> where = query.where();
                        where.and(where.eq(EventInstance.COLUMN_REF, ref),
                                where.gt(EventInstance.COLUMN_DATE_TIME, startTime));
                        query.setWhere(where);
                        int removed = 0;
                        for (EventInstance event : query.query()) {
                            if (event.getId() == null) {
                                throw new SQLException("Future event has no persisted identity");
                            }
                            if (event.completed() || event.cancelled()) {
                                continue;
                            }
                            if (dao.deleteById(event.getId()) != 1) {
                                throw new SQLException("Could not remove obsolete pending event");
                            }
                            removed++;
                        }
                        return removed;
                    });
        } catch (SQLException e) {
            throw new RuntimeException("Error removing unhandled future events", e);
        }
    }

    private enum UnassignedChange { COMPLETE, CANCEL, CONFIRM }

    /**
     * The null-patient lookup is already verified by device SQLite tests.
     * Update those exact persisted IDs transactionally rather than depending
     * on ORMLite bulk UPDATE WHERE null/foreign-key conversion behavior.
     */
    private int updateUnassignedEvents(EventType type, DateTime dateTime,
                                       DateTime completedAt, UnassignedChange change)
            throws SQLException {
        return com.j256.ormlite.misc.TransactionManager.callInTransaction(
                dbHelper.getConnectionSource(), () -> {
                    int changed = 0;
                    for (EventInstance event : find(type, dateTime, null)) {
                        if (event.getPatient() != null || event.getId() == null) {
                            throw new SQLException("Unexpected event identity during unassigned update");
                        }
                        if (event.completed()
                                || (change != UnassignedChange.CONFIRM && event.cancelled())) {
                            // Completing/cancelling must never rewrite a dose
                            // already cancelled; preserve its original timestamp.
                            // Non-medication CONFIRM may explicitly re-open it.
                            continue;
                        }
                        if (change == UnassignedChange.CANCEL) {
                            event.setCancelled(true);
                        } else {
                            event.setCompleted(true);
                            if (change == UnassignedChange.CONFIRM) {
                                event.setCancelled(false);
                            }
                        }
                        event.setCompletedAt(completedAt);
                        changed += dao.update(event);
                    }
                    return changed;
                });
    }

    public int checkAll(EventType type, DateTime dateTime, Patient p, DateTime completedAt) {
        // This legacy bulk method only updates event status and never adjusts
        // stock. Do not expose a second medication-confirmation route that can
        // silently bypass the atomic intake + inventory transaction.
        if (type == EventType.MEDICATION_INTAKE) {
            throw new IllegalStateException(
                    "Medication intake confirmation must include inventory transaction");
        }
        try {
            if (p == null) {
                // ORMLite's bulk update SQL does not reliably handle this
                // nullable foreign-key predicate on older Android SQLite.
                // Select with IS NULL, then update only persisted primary keys
                // in one transaction; never touch a different patient's row.
                return updateUnassignedEvents(type, dateTime, completedAt, UnassignedChange.COMPLETE);
            }
            UpdateBuilder<EventInstance, Long> qb = dao.updateBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                    (p == null ? w.isNull(EventInstance.COLUMN_PATIENT) : w.eq(EventInstance.COLUMN_PATIENT, p)),
                    w.eq(EventInstance.COLUMN_COMPLETED, false),
                    w.eq(EventInstance.COLUMN_CANCELLED, false)
            );
            qb.updateColumnValue(EventInstance.COLUMN_COMPLETED, true);
            qb.updateColumnValue(EventInstance.COLUMN_COMPLETED_DATETIME, completedAt);
            qb.setWhere(w);
            return qb.update();
        } catch (SQLException e) {

            throw new RuntimeException("Error finding event instances", e);
        }
    }

    /**
     * A per-row, scoped update avoids a fragile ORMLite bulk UPDATE involving
     * foreign Patient and primitive boolean predicates on legacy SQLite
     * schemas. Only rows still owned by the requested patient may change.
     * The entire selection and write batch is atomic.
     */
    private int cancelAssignedPendingEvents(
            EventType type, DateTime dateTime, Patient owner, DateTime completedAt)
            throws SQLException {
        if (owner.getId() == null || owner.getId() <= 0) {
            throw new SQLException("A persisted patient ID is required to cancel events");
        }
        return com.j256.ormlite.misc.TransactionManager.callInTransaction(
                dbHelper.getConnectionSource(), () -> {
                    int changed = 0;
                    for (EventInstance event : find(type, dateTime, owner)) {
                        if (event.getId() == null || event.getPatient() == null
                                || !owner.getId().equals(event.getPatient().getId())) {
                            throw new SQLException("Cancellation selection crossed patient boundary");
                        }
                        if (event.completed() || event.cancelled()) {
                            continue;
                        }
                        event.setCancelled(true);
                        event.setCompletedAt(completedAt);
                        if (dao.update(event) != 1) {
                            throw new SQLException("Failed to persist a patient-scoped cancellation");
                        }
                        changed++;
                    }
                    return changed;
                });
    }

    public int cancelUncompleted(EventType type, DateTime dateTime, Patient p, DateTime completedAt) {
        try {
            if (p == null) {
                // ORMLite's bulk update SQL does not reliably handle this
                // nullable foreign-key predicate on older Android SQLite.
                // Select with IS NULL, then update only persisted primary keys
                // in one transaction; never touch a different patient's row.
                return updateUnassignedEvents(type, dateTime, completedAt, UnassignedChange.CANCEL);
            }
            return cancelAssignedPendingEvents(type, dateTime, p, completedAt);
        } catch (SQLException e) {

            throw new RuntimeException("Error finding event instances", e);
        }
    }

    /**
     * Bulk status-only confirmation is never safe for medication intakes:
     * it cannot update the linked medicine stock in the same SQLite unit of
     * work. Use the transaction-backed ScheduleUtils/Agenda intake paths.
     *
     * Non-medication events retain the legacy bulk update semantics.
     */
    public int confirm(EventType type, DateTime dateTime, Patient p, DateTime completedAt) {
        if (type == EventType.MEDICATION_INTAKE) {
            throw new IllegalStateException(
                    "Medication intake confirmation must include inventory transaction");
        }
        try {
            if (p == null) {
                // ORMLite's bulk update SQL does not reliably handle this
                // nullable foreign-key predicate on older Android SQLite.
                // Select with IS NULL, then update only persisted primary keys
                // in one transaction; never touch a different patient's row.
                return updateUnassignedEvents(type, dateTime, completedAt, UnassignedChange.CONFIRM);
            }
            UpdateBuilder<EventInstance, Long> qb = dao.updateBuilder();
            Where w = qb.where();
            w.and(w.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                    w.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                    (p == null ? w.isNull(EventInstance.COLUMN_PATIENT) : w.eq(EventInstance.COLUMN_PATIENT, p)),
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

    /**
     * Rescheduling may change the dose of a still-pending occurrence, but a
     * completed or cancelled occurrence is a historical decision. Its dose
     * snapshot must not be rewritten by a later prescription edit.
     *
     * Select eligible rows and update only Params by persisted primary key
     * within the same transaction. This avoids overwriting status fields
     * from a stale EventInstance and works across legacy SQLite versions.
     */
    public int updateParams(EventType type, DateTime dateTime, Long ref, Bundle params) {
        try {
            return com.j256.ormlite.misc.TransactionManager.callInTransaction(
                    dbHelper.getConnectionSource(), () -> {
                        QueryBuilder<EventInstance, Long> query = dao.queryBuilder();
                        Where<EventInstance, Long> where = query.where();
                        where.and(where.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                                where.eq(EventInstance.COLUMN_DATE_TIME, dateTime),
                                where.eq(EventInstance.COLUMN_REF, ref));
                        query.setWhere(where);
                        int changed = 0;
                        for (EventInstance event : query.query()) {
                            if (event.getId() == null) {
                                throw new SQLException("Schedule event has no persisted ID");
                            }
                            if (event.completed() || event.cancelled()) {
                                continue;
                            }
                            UpdateBuilder<EventInstance, Long> update = dao.updateBuilder();
                            update.updateColumnValue(EventInstance.COLUMN_PARAMS, params);
                            update.where().eq(EventInstance.COLUMN_ID, event.getId());
                            if (update.update() != 1) {
                                throw new SQLException("Expected one pending dose parameter update");
                            }
                            changed++;
                        }
                        return changed;
                    });
        } catch (SQLException e) {
            throw new RuntimeException("Error updating pending event params", e);
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


    /**
     * Regenerating the agenda may prune obsolete unhandled occurrences.
     * A taken/skipped medication event is a historical decision, even when
     * its planned time lies outside the current agenda's display window.
     * Never delete such records as an incidental daily refresh.
     *
     * This deliberately does not define an automatic history-retention period:
     * privacy-controlled history removal is a separate explicit workflow.
     */
    private int pruneUnhandledByTime(
            EventType type, DateTime boundary, boolean olderThan) {
        try {
            return com.j256.ormlite.misc.TransactionManager.callInTransaction(
                    dbHelper.getConnectionSource(), () -> {
                        QueryBuilder<EventInstance, Long> query = dao.queryBuilder();
                        Where<EventInstance, Long> where = query.where();
                        if (olderThan) {
                            where.and(where.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                                    where.lt(EventInstance.COLUMN_DATE_TIME, boundary));
                        } else {
                            where.and(where.eq(EventInstance.COLUMN_EVENT_TYPE, type),
                                    where.gt(EventInstance.COLUMN_DATE_TIME, boundary));
                        }
                        query.setWhere(where);
                        int removed = 0;
                        for (EventInstance event : query.query()) {
                            if (event.getId() == null) {
                                throw new SQLException("Agenda event has no persisted ID");
                            }
                            if (event.completed() || event.cancelled()) {
                                continue;
                            }
                            if (dao.deleteById(event.getId()) != 1) {
                                throw new SQLException("Could not prune obsolete pending agenda event");
                            }
                            removed++;
                        }
                        return removed;
                    });
        } catch (SQLException e) {
            throw new RuntimeException("Error pruning pending agenda occurrences", e);
        }
    }

    public int removeOlderThan(EventType type, DateTime dateTime) {
        return pruneUnhandledByTime(type, dateTime, true);
    }

    public int removeBeyond(EventType type, DateTime dateTime) {
        return pruneUnhandledByTime(type, dateTime, false);
    }
}
