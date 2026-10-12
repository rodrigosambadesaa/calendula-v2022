/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.app.Notification;
import androidx.core.app.NotificationCompat;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import java.util.List;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Uses a synthetic in-memory reminder; posts no patient information. */
@RunWith(AndroidJUnit4.class)
public class UnverifiedMedicationAlertSmokeTest {

    @Test
    public void expandedNotificationContainsOnlyPendingDosesOfSelectedPatient()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime when = DateTime.now().plusHours(9).withMillisOfSecond(0);
        Patient owner = new Patient();
        owner.setCode("ci-render-owner-" + System.nanoTime());
        owner.setName("Synthetic rendering owner");
        Patient other = new Patient();
        other.setCode("ci-render-other-" + System.nanoTime());
        other.setName("Synthetic rendering other");
        String[] names = {
                "Synthetic VISIBLE medication",
                "Synthetic ALREADY TAKEN medication",
                "Synthetic CANCELLED medication",
                "Synthetic OTHER PATIENT medication"
        };
        Medicine[] medicines = new Medicine[4];
        Schedule[] schedules = new Schedule[4];
        EventInstance[] events = new EventInstance[4];
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(other));
            for (int i = 0; i < names.length; i++) {
                Patient patient = i == 3 ? other : owner;
                medicines[i] = new Medicine(names[i], Presentation.PILLS);
                medicines[i].setPatient(patient);
                assertEquals(1, DB.medicines().create(medicines[i]));
                schedules[i] = new Schedule(medicines[i]);
                schedules[i].setPatient(patient);
                assertEquals(1, DB.schedules().create(schedules[i]));
                events[i] = new EventInstance(when, EventType.MEDICATION_INTAKE);
                events[i].setPatient(patient);
                events[i].setRef(schedules[i].getId());
                events[i].addParam(EventInstance.PARAM_DOSE, 1.0d);
                if (i == 1) events[i].setCompleted(true);
                if (i == 2) events[i].setCancelled(true);
                assertEquals(1, DB.eventInstances().create(events[i]));
            }

            List<EventInstance> pending = DB.eventInstances().findPending(
                    EventType.MEDICATION_INTAKE, when, owner);
            assertEquals("Rendered notification must select only the one genuinely pending"
                    + " dose for the selected patient", 1, pending.size());
            assertEquals(events[0].getId(), pending.get(0).getId());

            // Exercise the same line renderer used by the production
            // notification InboxStyle, then inspect Android's actual extras.
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            for (EventInstance event : pending) {
                Schedule verified = DB.schedules().findById(event.getRef());
                CharSequence line = IntakeNotificationMgr.verifiedNotificationLine(
                        context, event, verified, owner);
                assertNotNull(line);
                style.addLine(line);
            }
            Notification built = new NotificationCompat.Builder(
                    context, NotificationHelper.CHANNEL_MEDS_ID)
                    .setSmallIcon(R.drawable.ic_pill_small)
                    .setContentTitle("Synthetic pending dose notice")
                    .setStyle(style)
                    .build();
            CharSequence[] visibleLines = built.extras.getCharSequenceArray(
                    Notification.EXTRA_TEXT_LINES);
            assertNotNull(visibleLines);
            assertEquals("Only the pending dose may appear in the expanded notice",
                    1, visibleLines.length);
            assertTrue(visibleLines[0].toString().contains(names[0]));
            for (int i = 1; i < names.length; i++) {
                assertFalse("Never expose completed, cancelled, or another patient's drugs",
                        visibleLines[0].toString().contains(names[i]));
            }

            // The shared formatter itself must refuse any erroneously
            // supplied non-pending or cross-patient event.
            for (int i = 1; i < names.length; i++) {
                assertNull(IntakeNotificationMgr.verifiedNotificationLine(
                        context, events[i], schedules[i], owner));
            }
        } finally {
            for (EventInstance event : events) {
                if (event != null && event.getId() != null
                        && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            for (Schedule schedule : schedules) {
                if (schedule != null && schedule.getId() != null
                        && DB.schedules().findById(schedule.getId()) != null) {
                    DB.schedules().remove(schedule);
                }
            }
            for (Medicine medicine : medicines) {
                if (medicine != null && medicine.getId() != null
                        && DB.medicines().findById(medicine.getId()) != null) {
                    DB.medicines().remove(medicine);
                }
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
            if (other.getId() != null && DB.patients().findById(other.getId()) != null) {
                DB.patients().remove(other);
            }
        }
    }

    @Test
    public void missingPatientDetailsBuildReviewOnlyNotificationWithoutMedicationActions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(6);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setId(9000003320L);
        reminder.setNextTime(time);
        Notification notification = IntakeNotificationMgr.buildUnverifiedMedicationNotification(
                context, reminder);
        try {
            assertEquals(context.getString(R.string.medication_unverified_title),
                    notification.extras.getString(Notification.EXTRA_TITLE));
            assertEquals(context.getString(R.string.medication_unverified_description),
                    notification.extras.getString(Notification.EXTRA_TEXT));
            assertTrue("Must not expose 'taken', 'cancel' or 'delay' actions",
                    notification.actions == null || notification.actions.length == 0);
            assertNotNull("Generic verification alert must navigate to app",
                    notification.contentIntent);
        } finally {
            if (notification.contentIntent != null) {
                notification.contentIntent.cancel();
            }
        }
    }
}
