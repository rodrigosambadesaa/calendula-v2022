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
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Real Android sqlite + AlarmManager smoke for orphan reminders.
 * Creates and removes only a synthetic record in the disposable emulator app.
 */
@RunWith(AndroidJUnit4.class)
public class OrphanReminderAlarmSmokeTest {

    private static PendingIntent findToken(Context context, EventReminder r) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, r),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    public void orphanAlarmDeliveryDeletesSqliteBeforeCancellingOsAlarm() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusHours(11).withMillisOfSecond(0);
        EventReminder orphan = new EventReminder(future, EventType.MEDICATION_INTAKE);
        orphan.setNextTime(future);

        assertFalse("Synthetic future event must be unassigned",
                DB.eventInstances().exists(EventType.MEDICATION_INTAKE, future));
        try {
            DB.eventReminders().save(orphan);
            assertNotNull(orphan.getId());
            Agenda.instance().setAlarm(context, orphan);
            assertNotNull(findToken(context, orphan));

            // Invoke the same handler called after a real AlarmReceiver dispatch;
            // no broadcast or medical notification is actually delivered.
            Agenda.instance().onReceiveAlarm(context, orphan.getId());

            assertNull("Delivered orphan must be gone from disposable SQLite",
                    DB.eventReminders().findById(orphan.getId()));
            assertNull("Delivered orphan must not leave a PendingIntent",
                    findToken(context, orphan));
        } finally {
            if (orphan.getId() != null) {
                Agenda.instance().cancelAlarm(context, orphan);
                if (DB.eventReminders().findById(orphan.getId()) != null) {
                    DB.eventReminders().remove(orphan);
                }
            }
        }
    }
}
