/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import android.content.Context;

import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;

/**
 * Crash-recoverable reconciliation for schedules released from HIGH allergy
 * blocking. The marker is written in the SAME SQLite transaction that removes
 * the BLOCKED state. It is cleared only after occurrences and OS reminders
 * have been reconciled successfully.
 *
 * Java-level replay is serialized across startup, boot and UI callbacks
 * so two concurrent invocations cannot both regenerate the same pending
 * schedule. This is process-local concurrency control, NOT a replacement for
 * SQLite owner checks, OS idempotence or physical-device acceptance.
 *
 * Never infer the owner from a numeric SQLite row ID: restored patient,
 * medicine and schedule IDs may all be reused. A failed/mismatched row
 * retains its marker and can only be repaired by an authorized operation.
 */
public final class ScheduleAlarmRecovery {
    private static final String TAG = "ScheduleAlarmRecovery";
    private static final Schedule.ScheduleState MARK =
            Schedule.ScheduleState.PENDING_REMINDER_RECOVERY;

    private ScheduleAlarmRecovery() {
    }

    private static Patient snapshotOwner(Patient persisted) {
        if (!PatientOwnerGuard.hasStableIdentity(persisted)) {
            throw new IllegalStateException("Recovery requires a stable patient owner");
        }
        Patient identity = new Patient();
        identity.setId(persisted.getId());
        identity.setCode(persisted.getCode());
        return identity;
    }

    /**
     * Stable, domain-separated identity fingerprint for crash recovery.
     * This is a comparison key, not encryption. It must be stored inside the
     * SAME SQLite transaction as the unblocked schedule state.
     */
    public static String ownerDigest(Patient owner) {
        if (!PatientOwnerGuard.hasStableIdentity(owner)) {
            throw new IllegalArgumentException("Recovery requires a stable owner");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update("calendula/high-alert-recovery/v1"
                    .getBytes(StandardCharsets.UTF_8));
            sha.update((byte) 0);
            sha.update(Long.toString(owner.getId()).getBytes(StandardCharsets.UTF_8));
            sha.update((byte) 0);
            sha.update(owner.getCode().getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha.digest();
            char[] result = new char[hash.length * 2];
            final char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < hash.length; i++) {
                int b = hash[i] & 0xff;
                result[2 * i] = hex[b >>> 4];
                result[2 * i + 1] = hex[b & 0x0f];
            }
            return "sha256-v1:" + new String(result);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    /** Pure guard also used by isolated policy tests. */
    public static boolean eligible(Schedule schedule, Patient expected,
                                   Patient liveOwner, Medicine liveMedicine,
                                   boolean hasHighWarning) {
        return schedule != null && schedule.getId() != null
                && schedule.getId() > 0L
                && schedule.hasState(MARK)
                && PatientOwnerGuard.hasStableIdentity(expected)
                && schedule.getRecoveryOwnerDigest() != null
                && schedule.getRecoveryOwnerDigest().equals(ownerDigest(expected))
                && !schedule.hasState(Schedule.ScheduleState.BLOCKED)
                && schedule.getMedicine() != null
                && liveMedicine != null
                && schedule.getMedicine().getId() != null
                && schedule.getMedicine().getId().equals(liveMedicine.getId())
                && PatientOwnerGuard.sameStableIdentity(expected, liveOwner)
                && PatientOwnerGuard.sameStableIdentity(expected, schedule.getPatient())
                && PatientOwnerGuard.sameStableIdentity(expected, liveMedicine.getPatient())
                && !hasHighWarning;
    }

    private static Schedule requireEligible(Long scheduleId, Patient expected)
            throws SQLException {
        if (scheduleId == null || scheduleId <= 0L
                || !PatientOwnerGuard.hasStableIdentity(expected)) {
            throw new SQLException("Incomplete schedule recovery identity");
        }
        // Force a NEW ORM instance rather than trusting a previously cached
        // Schedule whose patient/medicine foreign keys may have been reused.
        Schedule live = new Schedule();
        live.setId(scheduleId);
        if (DB.schedules().refresh(live) != 1) {
            throw new SQLException("Schedule was removed before recovery");
        }
        Medicine medicine = live.getMedicine() == null
                || live.getMedicine().getId() == null ? null
                : DB.medicines().findById(live.getMedicine().getId());
        if (medicine == null || DB.medicines().refresh(medicine) != 1) {
            throw new SQLException("Medicine was removed before recovery");
        }
        Patient owner = DB.patients().findById(expected.getId());
        boolean hasHigh = !DB.alerts().findByMedicineAndLevel(
                medicine, es.usc.citius.servando.calendula.persistence.PatientAlert.Level.HIGH)
                .isEmpty();
        if (!eligible(live, expected, owner, medicine, hasHigh)) {
            throw new SQLException("Schedule is no longer safe for recovery");
        }
        return live;
    }

    /**
     * Called AFTER the transaction that releases a schedule. If the app dies
     * after SQL COMMIT but before this returns, the marker remains persisted.
     * Duplicate replay cannot duplicate completed/cancelled intake decisions:
     * Agenda.addEvents uses the stable schedule/time identity.
     */
    public static synchronized void recoverOne(
            Context context, Long scheduleId, Patient expected) {
        if (context == null) {
            throw new IllegalArgumentException("Recovery requires application context");
        }
        final Patient owner = snapshotOwner(expected);
        Schedule current;
        try {
            current = requireEligible(scheduleId, owner);
        } catch (SQLException invalid) {
            throw new IllegalStateException("Unsafe schedule recovery refused", invalid);
        }

        // All platform alarm calls run OUTSIDE SQLite transactions. Do not
        // clear the marker if event persistence or OS registration fails.
        ScheduleUtils.instance().createEventInstances(context, current);
        Agenda.instance().cleanReminders(context);

        DB.transaction(() -> {
            Schedule unchanged = requireEligible(scheduleId, owner);
            unchanged.removeState(MARK);
            unchanged.setRecoveryOwnerDigest(null);
            if (DB.schedules().update(unchanged) != 1) {
                throw new SQLException("Could not commit schedule recovery marker");
            }
            return null;
        });
    }

    /**
     * Startup, boot, app upgrade, clock and exact-alarm permission recovery.
     * Each failed row stays marked, while other patients' verified rows can
     * still be reconciled. Never log medicine, owner or schedule identifiers.
     */
    public static synchronized void replayPending(Context context) {
        if (context == null || !DB.initialized) {
            return;
        }
        List<Schedule> pending = new ArrayList<>();
        try {
            for (Schedule schedule : DB.schedules().findAll()) {
                if (schedule != null && schedule.hasState(MARK)) {
                    pending.add(schedule);
                }
            }
        } catch (RuntimeException unavailable) {
            LogUtil.e(TAG, "Unable to scan pending reminder recovery");
            return;
        }
        for (Schedule candidate : pending) {
            try {
                if (candidate.getPatient() == null) {
                    continue;
                }
                recoverOne(context, candidate.getId(), candidate.getPatient());
            } catch (RuntimeException incomplete) {
                LogUtil.w(TAG, "Pending schedule recovery remains retryable");
            }
        }
    }
}
