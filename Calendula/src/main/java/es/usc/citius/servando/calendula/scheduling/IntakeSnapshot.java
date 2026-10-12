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
import java.util.Map;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Schedule;

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
    // Bound potentially corrupted imported/legacy Bundle content before
    // canonicalization. Exceeding either bound makes the action review-only,
    // never an incomplete subset of authorized intakes.
    private static final int MAX_VISIBLE_INTAKES = 256;
    private static final int MAX_DOSE_BYTES = 256;
    private static final int MAX_MEDICINE_NAME_BYTES = 1024;

    private IntakeSnapshot() { }

    /** Returns null for incomplete or duplicate persisted event identities. */
    public static String fingerprint(List<EventInstance> visible) {
        if (visible == null || visible.isEmpty()
                || visible.size() > MAX_VISIBLE_INTAKES) {
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
                Object rawDose = event.getParams() == null ? null
                        : event.getParams().get(EventInstance.PARAM_DOSE);
                // Bundle.getString() can silently return null when an old
                // parcel contains a numeric or otherwise wrong-typed dose.
                // Null has meaning for some official prescriptions; do not
                // confuse that valid absence with corrupted stored metadata.
                if (rawDose != null && !(rawDose instanceof String)) {
                    return null;
                }
                dose = (String) rawDose;
                if (dose != null
                        && (dose.length() > MAX_DOSE_BYTES
                        || dose.getBytes(StandardCharsets.UTF_8).length > MAX_DOSE_BYTES)) {
                    return null;
                }
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
        return fingerprint(visible, reminder, null);
    }

    /**
     * Bind consent to the displayed clinical source as well as the event.
     * When rendering a notification, pass the verified schedules that were
     * used to produce its text. During the SQLite commit, passing null forces
     * a new authoritative lookup instead of trusting a stale UI object.
     */
    public static String fingerprint(List<EventInstance> visible, EventReminder reminder,
                                     Map<Long, Schedule> displayedSchedules) {
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
        // Previously the digest only covered event.ref and dose. An in-place
        // schedule update could silently substitute a different medicine.
        // Include the actual source identity and visible presentation in the
        // consent hash, using canonical persisted event-ID ordering.
        List<EventInstance> sorted = new ArrayList<>(visible);
        Collections.sort(sorted, (a, b) -> Long.compare(a.getId(), b.getId()));
        for (EventInstance event : sorted) {
            Schedule schedule = displayedSchedules == null
                    ? DB.schedules().findById(event.getRef())
                    : displayedSchedules.get(event.getId());
            if (schedule == null || schedule.getId() == null
                    || !event.getRef().equals(schedule.getId())
                    || schedule.getPatient() == null
                    || schedule.getPatient().getId() == null
                    || !event.getPatient().getId().equals(schedule.getPatient().getId())) {
                return null;
            }
            Medicine medicine = schedule.getMedicine();
            if (medicine == null || medicine.getId() == null
                    || medicine.getName() == null) {
                return null;
            }
            String medicineName = medicine.getName();
            // Imported or damaged catalog entries must not cause unbounded
            // StringBuilder/UTF-8 allocations while creating consent tokens.
            // Do not truncate a name: consent must cover the exact displayed
            // clinical source or become non-actionable.
            if (medicineName.length() > MAX_MEDICINE_NAME_BYTES
                    || medicineName.getBytes(StandardCharsets.UTF_8).length
                    > MAX_MEDICINE_NAME_BYTES
                    || medicineName.trim().isEmpty()) {
                return null;
            }
            append(combined, Long.toString(event.getId()));
            append(combined, Long.toString(schedule.getId()));
            append(combined, Long.toString(schedule.getPatient().getId()));
            append(combined, Long.toString(medicine.getId()));
            append(combined, medicineName);
            append(combined, medicine.getPresentation() == null
                    ? null : medicine.getPresentation().toString());
            append(combined, medicine.getPatient() == null
                    || medicine.getPatient().getId() == null
                    ? null : Long.toString(medicine.getPatient().getId()));
            append(combined, schedule.getState() == null
                    ? null : schedule.getState().toString());
            append(combined, schedule.getActiveMedId() == null
                    ? null : Long.toString(schedule.getActiveMedId()));
        }
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
