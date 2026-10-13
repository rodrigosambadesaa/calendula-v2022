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
    public void databaseFutureCandidateQueryExcludesPastAndMissingNextTime() throws Exception {
        assertTrue(DB.initialized);
        DateTime now = DateTime.now().withMillisOfSecond(0);
        EventReminder upcoming = new EventReminder(
                now.plusMinutes(32), EventType.MEDICATION_INTAKE);
        upcoming.setNextTime(now.plusMinutes(32));
        EventReminder expired = new EventReminder(
                now.minusMinutes(42), EventType.MEDICATION_INTAKE);
        expired.setNextTime(now.minusMinutes(42));
        EventReminder incomplete = new EventReminder(
                now.plusMinutes(33), EventType.MEDICATION_INTAKE);
        incomplete.setNextTime(null);
        EventReminder[] fixture = {upcoming, expired, incomplete};
        try {
            for (EventReminder reminder : fixture) {
                assertTrue(DB.eventReminders().create(reminder) == 1);
                assertNotNull(reminder.getId());
            }
            java.util.Set<Long> eligibleIds = new java.util.HashSet<>();
            for (EventReminder reminder : DB.eventReminders().findFuture(now)) {
                eligibleIds.add(reminder.getId());
            }
            assertTrue("Only actual future next-alarm times are eligible",
                    eligibleIds.contains(upcoming.getId()));
            assertTrue("Expired record must stay in SQLite but not be loaded for rearm",
                    !eligibleIds.contains(expired.getId()));
            assertTrue("Malformed null next-time record must not be loaded",
                    !eligibleIds.contains(incomplete.getId()));
            assertNotNull("Filtering never deletes the historical row",
                    DB.eventReminders().findById(expired.getId()));
            assertNotNull(DB.eventReminders().findById(incomplete.getId()));
        } finally {
            for (EventReminder reminder : fixture) {
                if (reminder.getId() != null
                        && DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
        }
    }

    @Test
    public void malformedPersistedReminderRetiresOldAlarmButPreservesSqliteRecord() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusHours(14).withMillisOfSecond(0);
        EventReminder malformed = new EventReminder(
                future, EventType.MEDICATION_INTAKE);
        // Partially migrated legacy row: there is no authoritative next time.
        malformed.setNextTime(null);
        try {
            DB.eventReminders().save(malformed);
            assertNotNull(malformed.getId());
            android.app.AlarmManager manager = (android.app.AlarmManager)
                    context.getSystemService(Context.ALARM_SERVICE);
            assertNotNull(manager);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                    Agenda.reminderBroadcastIntent(context, malformed),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
            assertNotNull(pendingIntent);
            manager.set(android.app.AlarmManager.RTC_WAKEUP,
                    future.getMillis(), pendingIntent);
            assertNotNull("Synthetic old OS token exists before delivery",
                    token(context, malformed));

            Agenda.instance().onReceiveAlarm(context, malformed.getId());

            assertNull("Broken reminder must no longer wake the device",
                    token(context, malformed));
            assertNotNull("Retain incomplete row for migration",
                    DB.eventReminders().findById(malformed.getId()));
            assertNull(DB.eventReminders().findById(malformed.getId()).getNextTime());
        } finally {
            if (malformed.getId() != null) {
                Agenda.instance().cancelAlarm(context, malformed);
                if (DB.eventReminders().findById(malformed.getId()) != null) {
                    DB.eventReminders().remove(malformed);
                }
            }
        }
    }

    @Test
    public void newLegacyEventsCannotScheduleUndeliverableAlarm() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusHours(13).withMillisOfSecond(0);
        EventInstance legacy = new EventInstance(future, EventType.STOCK_REMINDER);
        try {
            DB.eventInstances().save(legacy);
            assertNotNull(legacy.getId());
            assertTrue("Synthetic stock event must be pending",
                    DB.eventInstances().existsPending(EventType.STOCK_REMINDER, future, null));

            assertTrue("A no-op must not report a transactional error",
                    Agenda.instance().createReminders(context,
                            java.util.Collections.singletonList(legacy)));
            assertNull("Unsupported event cannot create a persisted reminder",
                    DB.eventReminders().findBy(EventType.STOCK_REMINDER, future, null));
            assertTrue("Do not mark an unsupported event completed or cancelled",
                    !DB.eventInstances().findById(legacy.getId()).completed()
                            && !DB.eventInstances().findById(legacy.getId()).cancelled());
        } finally {
            EventReminder accidental = DB.eventReminders().findBy(
                    EventType.STOCK_REMINDER, future, null);
            if (accidental != null) {
                Agenda.instance().cancelAlarm(context, accidental);
                DB.eventReminders().remove(accidental);
            }
            if (legacy.getId() != null && DB.eventInstances().findById(legacy.getId()) != null) {
                DB.eventInstances().remove(legacy);
            }
        }
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
    public void retryCreateRemindersRecoversCommittedFutureAlarmWithoutDuplicates() {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusMinutes(38).withMillisOfSecond(0);
        DateTime handledTime = future.plusMinutes(1);
        DateTime past = DateTime.now().minusMinutes(8).withMillisOfSecond(0);
        EventInstance pending = new EventInstance(future, EventType.MEDICATION_INTAKE);
        EventInstance cancelled = new EventInstance(handledTime, EventType.MEDICATION_INTAKE);
        cancelled.setCancelled(true);
        EventInstance oldPending = new EventInstance(past, EventType.MEDICATION_INTAKE);
        EventInstance[] events = {pending, cancelled, oldPending};
        EventReminder live = new EventReminder(future, EventType.MEDICATION_INTAKE);
        live.setNextTime(future);
        EventReminder handled = new EventReminder(handledTime, EventType.MEDICATION_INTAKE);
        handled.setNextTime(handledTime);
        EventReminder old = new EventReminder(past, EventType.MEDICATION_INTAKE);
        old.setNextTime(past);
        EventReminder[] reminders = {live, handled, old};
        try {
            for (EventInstance e : events) {
                DB.eventInstances().save(e);
                assertNotNull(e.getId());
            }
            for (EventReminder reminder : reminders) {
                DB.eventReminders().save(reminder);
                assertNotNull(reminder.getId());
                Agenda.instance().cancelAlarm(ctx, reminder);
                assertNull("Simulated post-commit interruption has no OS alarm",
                        token(ctx, reminder));
            }
            // The rows already exist, so the old path skipped alarm setup.
            // Retrying now must repair the future registration without
            // inserting another row, reviving a cancelled dose or firing a
            // stale historical reminder.
            assertTrue(Agenda.instance().createReminders(ctx,
                    java.util.Arrays.asList(pending, cancelled, oldPending)));
            assertNotNull("Pending future alarm was recovered",
                    token(ctx, live));
            assertNull("Cancelled dose must not reappear", token(ctx, handled));
            assertNull("Past reminder must not be scheduled again", token(ctx, old));
            assertNotNull(DB.eventReminders().findById(live.getId()));
            assertTrue(Agenda.instance().createReminders(ctx,
                    java.util.Collections.singletonList(pending)));
            assertNotNull("Second retry is idempotent", token(ctx, live));
            assertTrue("No duplicate reminder row created",
                    DB.eventReminders().findBy(
                            EventType.MEDICATION_INTAKE, future, null).getId().equals(live.getId()));
        } finally {
            for (EventReminder reminder : reminders) {
                if (reminder.getId() != null) {
                    Agenda.instance().cancelAlarm(ctx, reminder);
                    if (DB.eventReminders().findById(reminder.getId()) != null) {
                        DB.eventReminders().remove(reminder);
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
