/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

/**
 * Compact, non-reversible consent snapshot for notification "Taken" actions.
 * Captures ALL scheduled intake rows for the patient and time, not only rows
 * still pending: changes after notification display invalidate old actions.
 * The digest contains no literal patient IDs, names, doses or timestamps.
 */
public final class MedicationIntakeSnapshot {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int MAX_EVENTS = 256;
    private static final int MAX_DOSE_BYTES = 256;

    private MedicationIntakeSnapshot() {}

    /**
     * Returns null for an incomplete or empty set: no automatic notification
     * confirmation may proceed if its displayed clinical context is unclear.
     */
    public static String fingerprint(List<EventInstance> instances) {
        if (instances == null || instances.isEmpty() || instances.size() > MAX_EVENTS) {
            return null;
        }
        List<EventInstance> ordered = new ArrayList<>(instances);
        for (EventInstance event : ordered) {
            if (event == null || event.getId() == null || event.getId() <= 0L
                    || event.getRef() == null || event.getRef() <= 0L
                    || event.getTime() == null || event.getType() != EventType.MEDICATION_INTAKE
                    || event.getPatient() == null || event.getPatient().getId() == null
                    || event.getPatient().getId() <= 0L) {
                return null;
            }
        }
        Collections.sort(ordered, (first, second) -> Long.compare(first.getId(), second.getId()));
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1); // Fingerprint format version.
            out.writeInt(ordered.size());
            Long previousId = null;
            for (EventInstance event : ordered) {
                if (event.getId().equals(previousId)) {
                    return null;
                }
                previousId = event.getId();
                out.writeLong(event.getId());
                out.writeLong(event.getRef());
                out.writeLong(event.getPatient().getId());
                out.writeLong(event.getTime().getMillis());
                out.writeBoolean(event.completed());
                out.writeBoolean(event.cancelled());
                String dose;
                try {
                    Object rawDose = event.getParams() == null ? null
                            : event.getParams().get(EventInstance.PARAM_DOSE);
                    // A malformed restored Bundle may contain a numeric or
                    // parcelled value. Never silently omit or coerce a dose:
                    // doing so would authorize a notification with a different
                    // clinical snapshot from the one originally displayed.
                    if (rawDose != null && !(rawDose instanceof String)) {
                        return null;
                    }
                    dose = (String) rawDose;
                } catch (RuntimeException invalidLegacyBundle) {
                    return null;
                }
                out.writeBoolean(dose != null);
                if (dose != null) {
                    byte[] encoded = dose.getBytes(StandardCharsets.UTF_8);
                    if (encoded.length > MAX_DOSE_BYTES) {
                        return null;
                    }
                    out.writeInt(encoded.length);
                    out.write(encoded);
                }
            }
            out.flush();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            char[] result = new char[digest.length * 2];
            for (int i = 0; i < digest.length; ++i) {
                result[2 * i] = HEX[(digest[i] & 0xff) >>> 4];
                result[2 * i + 1] = HEX[digest[i] & 0x0f];
            }
            return new String(result);
        } catch (IOException | NoSuchAlgorithmException unexpected) {
            throw new IllegalStateException("Cannot calculate medication snapshot", unexpected);
        }
    }
}
