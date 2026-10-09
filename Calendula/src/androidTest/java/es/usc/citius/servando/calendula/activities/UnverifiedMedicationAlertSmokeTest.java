/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.app.Notification;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Uses a synthetic in-memory reminder; posts no patient information. */
@RunWith(AndroidJUnit4.class)
public class UnverifiedMedicationAlertSmokeTest {

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
