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
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Recreates a post-COMMIT/pre-AlarmManager interruption using real SQLite and
 * Android PendingIntent tokens; no patient or real medicine data is used.
 */
@RunWith(AndroidJUnit4.class)
public class StartupReminderRearmSmokeTest {
    private static PendingIntent token(Context ctx, EventReminder reminder) {
        return PendingIntent.getBroadcast(ctx, 0,
                Agenda.reminderBroadcastIntent(ctx, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    public void rebootDoesNotArmLegacyReminderWithNoRegisteredReceiver() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusHours(11).withMillisOfSecond(0);
        EventInstance pharmacyEvent = new EventInstance(
                future, EventType.PHARMACY_REMINDER);
        EventReminder pharmacyReminder = new EventReminder(
                future, EventType.PHARMACY_REMINDER);
        pharmacyReminder.setNextTime(future);
        try {
            DB.eventInstances().save(pharmacyEvent);
            DB.eventReminders().save(pharmacyReminder);
            assertNotNull(pharmacyEvent.getId());
            assertNotNull(pharmacyReminder.getId());
            assertTrue("A pending legacy row must actually exist",
                    DB.eventInstances().existsPending(
                            EventType.PHARMACY_REMINDER, future, null));
            Agenda.instance().cancelAlarm(context, pharmacyReminder);
            assertNull(token(context, pharmacyReminder));

            Agenda.instance().rearmPendingFutureReminders(context);
            assertNull("Without a receiver, no OS alarm should be armed",
                    token(context, pharmacyReminder));
            Agenda.instance().updateAllAlarms(context);
            assertNull("Boot-time alarm recovery must use the same safety filter",
                    token(context, pharmacyReminder));
            assertNotNull("Keep legacy row for future migration or inspection",
                    DB.eventReminders().findById(pharmacyReminder.getId()));
            assertTrue("Legacy event must not be marked taken or cancelled",
                    !DB.eventInstances().findById(pharmacyEvent.getId()).completed()
                            && !DB.eventInstances().findById(pharmacyEvent.getId()).cancelled());
        } finally {
            if (pharmacyReminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, pharmacyReminder);
                if (DB.eventReminders().findById(pharmacyReminder.getId()) != null) {
                    DB.eventReminders().remove(pharmacyReminder);
                }
            }
            if (pharmacyEvent.getId() != null
                    && DB.eventInstances().findById(pharmacyEvent.getId()) != null) {
                DB.eventInstances().remove(pharmacyEvent);
            }
        }
    }

    @Test
    public void startupRearmsCommittedFutureIntakeButSkipsCancelledAndPastOnes() {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusHours(9).withMillisOfSecond(0);
        DateTime cancelledTime = future.plusMinutes(1);
        DateTime oldTime = DateTime.now().minusMinutes(5).withMillisOfSecond(0);

        EventInstance validEvent = new EventInstance(future, EventType.MEDICATION_INTAKE);
        EventInstance cancelledEvent = new EventInstance(cancelledTime, EventType.MEDICATION_INTAKE);
        cancelledEvent.setCancelled(true);
        EventInstance oldEvent = new EventInstance(oldTime, EventType.MEDICATION_INTAKE);
        EventInstance[] events = {validEvent, cancelledEvent, oldEvent};

        EventReminder valid = new EventReminder(future, EventType.MEDICATION_INTAKE);
        valid.setNextTime(future);
        EventReminder cancelled = new EventReminder(cancelledTime, EventType.MEDICATION_INTAKE);
        cancelled.setNextTime(cancelledTime);
        EventReminder old = new EventReminder(oldTime, EventType.MEDICATION_INTAKE);
        old.setNextTime(oldTime);
        EventReminder[] reminders = {valid, cancelled, old};
        try {
            for (EventInstance e : events) {
                DB.eventInstances().save(e);
                assertNotNull(e.getId());
            }
            for (EventReminder r : reminders) {
                DB.eventReminders().save(r);
                assertNotNull(r.getId());
                Agenda.instance().cancelAlarm(ctx, r);
                assertNull("No platform alarm should exist before restart simulation", token(ctx, r));
            }

            Agenda.instance().rearmPendingFutureReminders(ctx);

            assertNotNull("Committed future reminder must regain its stable OS alarm",
                    token(ctx, valid));
            assertNull("Cancelled dose must not receive another alarm",
                    token(ctx, cancelled));
            assertNull("Past-due reminder must not be blindly triggered again",
                    token(ctx, old));

            // Device reboot, time changes and exact-alarm permission changes
            // use updateAllAlarms, not the startup-only recovery entrypoint.
            // Both paths must enforce the same future/pending filter.
            Agenda.instance().updateAllAlarms(ctx);
            assertNotNull("Device recovery must retain the valid future alarm",
                    token(ctx, valid));
            assertNull("Device recovery must not resurrect cancelled medication",
                    token(ctx, cancelled));
            assertNull("Device recovery must not immediately trigger expired doses",
                    token(ctx, old));

            // A second startup must reuse the same stable PendingIntent identity.
            PendingIntent first = token(ctx, valid);
            Agenda.instance().rearmPendingFutureReminders(ctx);
            assertNotNull(token(ctx, valid));
            assertTrue(first.equals(token(ctx, valid)));
        } finally {
            for (EventReminder r : reminders) {
                if (r.getId() != null) {
                    Agenda.instance().cancelAlarm(ctx, r);
                    if (DB.eventReminders().findById(r.getId()) != null) {
                        DB.eventReminders().remove(r);
                    }
                }
            }
            for (EventInstance e : events) {
                if (e.getId() != null && DB.eventInstances().findById(e.getId()) != null) {
                    DB.eventInstances().remove(e);
                }
            }
        }
    }
}
