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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Genuine SQLite and AlarmManager, disposable synthetic reminder only. */
@RunWith(AndroidJUnit4.class)
public class PublicReminderPostCommitSmokeTest {

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
