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

import java.util.Collections;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Genuine SQLite and AlarmManager, disposable synthetic reminder only. */
@RunWith(AndroidJUnit4.class)
public class PublicReminderPostCommitSmokeTest {

    @Test
    public void supersededAlarmBroadcastNeverDeliversFutureDoseAndRearmsItsCurrentTime() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime currentDelivery = DateTime.now().plusMinutes(35).withMillisOfSecond(0);
        EventInstance pending = new EventInstance(currentDelivery, EventType.MEDICATION_INTAKE);
        EventReminder persisted = new EventReminder(currentDelivery, EventType.MEDICATION_INTAKE);
        persisted.setNextTime(currentDelivery);
        try {
            DB.eventInstances().save(pending);
            DB.eventReminders().save(persisted);
            assertNotNull(persisted.getId());
            assertNull("No OS alarm has been registered yet for this fixture",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, persisted),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));

            // Emulate an already queued alarm from the superseded delivery.
            // The latest persisted NextTime is still in the future.
            Agenda.instance().onReceiveAlarm(context, persisted.getId());
            assertEquals("A stale delivery cannot rewrite the time chosen by the user",
                    currentDelivery, DB.eventReminders().findById(persisted.getId()).getNextTime());
            assertTrue("A premature callback cannot mark a medication completed",
                    !DB.eventInstances().findById(pending.getId()).completed());
            assertNotNull("The current, future reminder must be rearmed instead of delivered early",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, persisted),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
        } finally {
            if (persisted.getId() != null) {
                Agenda.instance().cancelAlarm(context, persisted);
                if (DB.eventReminders().findById(persisted.getId()) != null) {
                    DB.eventReminders().remove(persisted);
                }
            }
            if (pending.getId() != null && DB.eventInstances().findById(pending.getId()) != null) {
                DB.eventInstances().remove(pending);
            }
        }
    }

    @Test
    public void prematureOrphanBroadcastDoesNotCreateAnAndroidAlarm() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusMinutes(40).withMillisOfSecond(0);
        EventReminder orphan = new EventReminder(future, EventType.MEDICATION_INTAKE);
        orphan.setNextTime(future);
        try {
            DB.eventReminders().save(orphan);
            Agenda.instance().onReceiveAlarm(context, orphan.getId());
            assertNull("A reminder with no pending intake must never rearm",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, orphan),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            assertNull("The old orphan cleanup must delete the invalid reminder",
                    DB.eventReminders().findById(orphan.getId()));
        } finally {
            if (orphan.getId() != null) {
                Agenda.instance().cancelAlarm(context, orphan);
                if (DB.eventReminders().findById(orphan.getId()) != null) {
                    DB.eventReminders().remove(orphan);
                }
            }
        }
    }

    @Test
    public void publicReminderCreationCommitsThenRegistersStableAndroidAlarm() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusMinutes(4).withMillisOfSecond(0);
        EventInstance event = new EventInstance(future, EventType.MEDICATION_INTAKE);
        EventReminder created = null;
        try {
            DB.eventInstances().save(event);
            assertNotNull(event.getId());
            assertTrue("The public path must persist and reconcile reminders",
                    Agenda.instance().createReminders(context,
                            Collections.singletonList(event)));

            created = DB.eventReminders().findBy(
                    EventType.MEDICATION_INTAKE, future, null);
            assertNotNull("Committed reminder row must exist in SQLite", created);
            assertNotNull(created.getId());
            assertNotNull("OS PendingIntent must be registered after persistence",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, created),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
        } finally {
            if (created != null && created.getId() != null) {
                Agenda.instance().cancelAlarm(context, created);
                if (DB.eventReminders().findById(created.getId()) != null) {
                    DB.eventReminders().remove(created);
                }
            }
            if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
            if (created != null && created.getId() != null) {
                assertNull("Synthetic record must be cleaned after the test",
                        DB.eventReminders().findById(created.getId()));
            }
        }
    }
}
