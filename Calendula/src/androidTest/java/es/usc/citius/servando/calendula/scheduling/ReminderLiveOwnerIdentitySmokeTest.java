/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.joda.time.DateTime;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Disposable SQLite-backed patient identity regression. Only synthetic rows;
 * never operate on a user's personal medication database.
 */
@RunWith(AndroidJUnit4.class)
public class ReminderLiveOwnerIdentitySmokeTest {

    @Test
    public void unidentifiedLegacyPatientCannotAuthorizeReminderUntilCodeIsMigrated()
            throws Exception {
        assertTrue(DB.initialized);
        Patient legacy = new Patient();
        legacy.setName("Synthetic legacy reminder owner");
        EventReminder reminder = new EventReminder(
                DateTime.now().plusHours(3), EventType.MEDICATION_INTAKE);
        try {
            assertEquals(1, DB.patients().create(legacy));
            assertNotNull(legacy.getId());
            reminder.setPatient(legacy);
            assertFalse("Missing stable code must not authorize clinical delivery",
                    Agenda.hasLiveReminderOwner(reminder));

            Patient migrated = DB.patients().findByIdWithLocalIdentity(legacy.getId());
            assertNotNull(migrated);
            assertNotNull(migrated.getCode());
            assertTrue("Legacy profile must obtain a persisted local identity",
                    migrated.getCode().startsWith("local-"));
            assertFalse("A stale reminder snapshot with no code stays untrusted",
                    Agenda.hasLiveReminderOwner(reminder));

            reminder.setPatient(migrated);
            assertTrue("Current matching stable identity may deliver",
                    Agenda.hasLiveReminderOwner(reminder));
            Patient otherWithRecycledId = new Patient();
            otherWithRecycledId.setId(legacy.getId());
            otherWithRecycledId.setCode("synthetic-different-owner");
            reminder.setPatient(otherWithRecycledId);
            assertFalse("Reused numeric ID cannot impersonate migrated owner",
                    Agenda.hasLiveReminderOwner(reminder));
        } finally {
            if (legacy.getId() != null
                    && DB.patients().findById(legacy.getId()) != null) {
                DB.patients().remove(legacy);
            }
        }
    }
}
