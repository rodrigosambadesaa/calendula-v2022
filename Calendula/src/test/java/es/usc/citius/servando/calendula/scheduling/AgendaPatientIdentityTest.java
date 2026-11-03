/*
 * Caléndula - A personal medication management assistant.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import org.joda.time.DateTime;
import org.junit.Test;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Synthetic event snapshots only; no patient or medication DB is modified. */
public class AgendaPatientIdentityTest {
    private static Patient patient(Long id, String code) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    private static EventInstance event(Patient patient) {
        EventInstance e = new EventInstance(new DateTime(2026, 11, 3, 8, 0),
                EventType.MEDICATION_INTAKE);
        e.setPatient(patient);
        e.setRef(1000L);
        return e;
    }

    @Test
    public void reusedRowIdMustNotDeduplicateDifferentPatientsMedicationOccurrence() {
        EventInstance original = event(patient(401L, "synthetic-original"));
        EventInstance reused = event(patient(401L, "synthetic-replacement"));
        assertFalse(Agenda.sameEventPatientIdentity(original, reused));
        assertFalse(Agenda.sameEventPatientIdentity(reused, original));

        EventInstance reloaded = event(patient(401L, "synthetic-original"));
        assertTrue(Agenda.sameEventPatientIdentity(original, reloaded));
        assertFalse(Agenda.sameEventPatientIdentity(
                original, event(patient(402L, "synthetic-original"))));
    }

    @Test
    public void unassignedLegacyRowsKeepOriginalSemanticsButBadIdsFailClosed() {
        assertTrue(Agenda.sameEventPatientIdentity(event(null), event(null)));
        assertFalse(Agenda.sameEventPatientIdentity(event(null),
                event(patient(401L, "synthetic"))));
        assertFalse(Agenda.sameEventPatientIdentity(
                event(patient(null, "synthetic")), event(patient(null, "synthetic"))));
        assertFalse(Agenda.sameEventPatientIdentity(
                event(patient(0L, "synthetic")), event(patient(0L, "synthetic"))));
        assertFalse(Agenda.sameEventPatientIdentity(null, event(null)));
        // Incompletely restored legacy codes retain the prior ID-only
        // behavior until they can be safely reconciled.
        assertTrue(Agenda.sameEventPatientIdentity(
                event(patient(401L, null)), event(patient(401L, "synthetic"))));
    }
}
