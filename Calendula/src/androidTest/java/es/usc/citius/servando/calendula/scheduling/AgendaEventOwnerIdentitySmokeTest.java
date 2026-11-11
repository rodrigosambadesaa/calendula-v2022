/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Real disposable SQLite regression. Synthetic patients and events only;
 * no medication schedule, stock, clinical notification or external service.
 */
@RunWith(AndroidJUnit4.class)
public class AgendaEventOwnerIdentitySmokeTest {

    private static EventInstance occurrence(Patient owner, Long ref, DateTime when) {
        EventInstance event = new EventInstance(when, EventType.MEDICATION_INTAKE);
        event.setPatient(owner);
        event.setRef(ref);
        return event;
    }

    @Test
    public void missingOrChangedOwnerCodeMustNotSilentlyDropOrOverwriteIntake()
            throws Exception {
        assertTrue(DB.initialized);
        Patient persistedOwner = new Patient();
        persistedOwner.setCode("ci-agenda-identity-" + System.nanoTime());
        persistedOwner.setName("Synthetic persisted owner");
        DateTime when = DateTime.now().plusDays(4).withMillisOfSecond(0);
        Long ref = Math.abs(System.nanoTime()) + 1000000000L;
        EventInstance original = null;
        try {
            assertEquals(1, DB.patients().create(persistedOwner));
            original = occurrence(persistedOwner, ref, when);
            assertEquals(1, DB.eventInstances().create(original));
            assertNotNull(original.getId());

            Patient recycled = new Patient();
            recycled.setId(persistedOwner.getId());
            recycled.setCode("ci-another-synthetic-owner");
            for (Patient invalid : new Patient[] {recycled, new Patient()}) {
                if (invalid.getId() == null) {
                    invalid.setId(persistedOwner.getId());
                }
                boolean rejected = false;
                try {
                    Agenda.instance().addEvents(Collections.singleton(
                            occurrence(invalid, ref, when)));
                } catch (IllegalStateException expected) {
                    rejected = true;
                }
                assertTrue("Missing or changed owner identity must reject deduplication",
                        rejected);
                assertNotNull("Existing clinical event must remain unchanged",
                        DB.eventInstances().findById(original.getId()));
                assertEquals("No silent insertion or deletion on rejected identity",
                        1, DB.eventInstances().findByTypeAndTime(
                                EventType.MEDICATION_INTAKE, when).stream()
                                .filter(event -> ref.equals(event.getRef())).count());
            }

            Patient matching = new Patient();
            matching.setId(persistedOwner.getId());
            matching.setCode(persistedOwner.getCode());
            Agenda.instance().addEvents(Collections.singleton(
                    occurrence(matching, ref, when)));
            assertEquals("Same identified occurrence remains idempotent",
                    1, DB.eventInstances().findByTypeAndTime(
                            EventType.MEDICATION_INTAKE, when).stream()
                            .filter(event -> ref.equals(event.getRef())).count());
        } finally {
            if (original != null && original.getId() != null
                    && DB.eventInstances().findById(original.getId()) != null) {
                DB.eventInstances().remove(original);
            }
            if (persistedOwner.getId() != null
                    && DB.patients().findById(persistedOwner.getId()) != null) {
                DB.patients().remove(persistedOwner);
            }
        }
    }
}
