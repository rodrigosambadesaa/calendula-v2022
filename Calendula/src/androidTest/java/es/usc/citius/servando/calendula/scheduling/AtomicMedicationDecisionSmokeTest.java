/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Uses only synthetic SQLite fixture rows and their AlarmManager tokens. */
@RunWith(AndroidJUnit4.class)
public class AtomicMedicationDecisionSmokeTest {
    private static PendingIntent token(Context context, EventReminder reminder) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    public void confirmingUnassignedIntakeCommitsEventAndRemovesAlarm() {
        exerciseDecision(true, false);
    }

    @Test
    public void cancellingAssignedIntakeCommitsEventAndRemovesAlarm() {
        exerciseDecision(false, true);
    }

    private void exerciseDecision(boolean confirm, boolean withPatient) {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(confirm ? 15 : 16).withMillisOfSecond(0);
        Patient synthetic = null;
        EventInstance event = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(time);
        try {
            if (withPatient) {
                synthetic = new Patient();
                synthetic.setCode("ci-decision-" + System.nanoTime());
                synthetic.setName("Synthetic medication test patient");
                DB.patients().save(synthetic);
                event.setPatient(synthetic);
                reminder.setPatient(synthetic);
            }
            DB.eventInstances().save(event);
            DB.eventReminders().save(reminder);
            assertNotNull(event.getId());
            assertNotNull(reminder.getId());
            Agenda.instance().setAlarm(context, reminder);
            assertNotNull("A synthetic alarm must exist before the decision",
                    token(context, reminder));

            if (confirm) {
                Agenda.instance().confirmReminder(context, reminder.getId());
            } else {
                Agenda.instance().cancelReminder(context, reminder.getId());
            }

            EventInstance updated = DB.eventInstances().findById(event.getId());
            assertNotNull("The original event must be preserved", updated);
            if (confirm) {
                assertTrue("The confirmed intake must be completed", updated.completed());
                assertFalse("Confirming an intake must not mark it cancelled", updated.cancelled());
            } else {
                assertTrue("Cancelling an intake must mark it cancelled", updated.cancelled());
                assertFalse("Cancelling must not mark it completed", updated.completed());
            }
            assertNull("The reminder must be deleted from SQLite",
                    DB.eventReminders().findById(reminder.getId()));
            assertNull("The matching Android alarm token must be retired",
                    token(context, reminder));
        } finally {
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
            if (synthetic != null && synthetic.getId() != null) {
                DB.patients().remove(synthetic);
            }
        }
    }
}
