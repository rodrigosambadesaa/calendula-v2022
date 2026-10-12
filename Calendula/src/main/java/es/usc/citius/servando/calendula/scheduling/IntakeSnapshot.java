/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

/**
 * An opaque digest of the exact pending doses visible in a notification.
 * No patient identifiers, medicine names or dose values are placed directly
 * in PendingIntent extras or logs. This is a stale-selection guard, not an
 * authentication token; clinical and ownership checks must still run inside
 * the SQLite write transaction.
 */
public final class IntakeSnapshot {
    private IntakeSnapshot() { }

    /** Returns null for incomplete or duplicate persisted event identities. */
    public static String fingerprint(List<EventInstance> visible) {
        if (visible == null || visible.isEmpty()) {
            return null;
        }
        List<EventInstance> sorted = new ArrayList<>(visible);
        HashSet<Long> seen = new HashSet<>();
        for (EventInstance event : sorted) {
            if (event == null || event.getId() == null || event.getId() <= 0L
                    || event.getRef() == null || event.getTime() == null
                    || event.getPatient() == null || event.getPatient().getId() == null
                    || event.getType() != EventType.MEDICATION_INTAKE
                    || event.completed() || event.cancelled()
                    || !seen.add(event.getId())) {
                return null;
            }
        }
        Collections.sort(sorted, (a, b) -> Long.compare(a.getId(), b.getId()));
        StringBuilder canonical = new StringBuilder();
        for (EventInstance event : sorted) {
            final String dose;
            try {
                dose = event.getParams() == null ? null
                        : event.getParams().getString(EventInstance.PARAM_DOSE);
            } catch (RuntimeException malformed) {
                return null;
            }
            append(canonical, Long.toString(event.getId()));
            append(canonical, event.getType().name());
            append(canonical, Long.toString(event.getPatient().getId()));
            append(canonical, Long.toString(event.getRef()));
            append(canonical, Long.toString(event.getTime().getMillis()));
            append(canonical, dose);
        }
        return digest(canonical.toString());
    }

    /**
     * Notification consent includes the exact reminder revision as well as
     * the displayed medication. A delayed/replaced reminder invalidates its
     * previous button even if every dose still has the same amount.
     */
    public static String fingerprint(List<EventInstance> visible, EventReminder reminder) {
        if (reminder == null || reminder.getId() == null || reminder.getId() <= 0L
                || reminder.getPatient() == null
                || reminder.getPatient().getId() == null
                || reminder.getEventType() != EventType.MEDICATION_INTAKE
                || reminder.getDateTime() == null || reminder.getNextTime() == null) {
            return null;
        }
        String doseFingerprint = fingerprint(visible);
        if (doseFingerprint == null) {
            return null;
        }
        StringBuilder combined = new StringBuilder("reminder-snapshot-v2:");
        append(combined, Long.toString(reminder.getId()));
        append(combined, Long.toString(reminder.getPatient().getId()));
        append(combined, reminder.getEventType().name());
        append(combined, Long.toString(reminder.getDateTime().getMillis()));
        append(combined, Long.toString(reminder.getNextTime().getMillis()));
        append(combined, doseFingerprint);
        return digest(combined.toString());
    }

    private static String digest(String canonical) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    canonical.getBytes(StandardCharsets.UTF_8));
            final char[] hex = "0123456789abcdef".toCharArray();
            char[] chars = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 0xff;
                chars[i * 2] = hex[value >>> 4];
                chars[i * 2 + 1] = hex[value & 15];
            }
            return new String(chars);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void append(StringBuilder canonical, String value) {
        canonical.append(value == null ? -1 : value.length()).append(':');
        if (value != null) {
            canonical.append(value);
        }
    }
}
