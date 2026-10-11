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
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Uses a synthetic in-memory reminder; posts no patient information. */
@RunWith(AndroidJUnit4.class)
public class UnverifiedMedicationAlertSmokeTest {

    @Test
    public void officialNotificationNeverRendersForeignTreatmentOnRealAndroid() {
        Patient owner = new Patient();
        owner.setId(9000003661L);
        Patient other = new Patient();
        other.setId(9000003662L);
        EventInstance event = new EventInstance(DateTime.now().plusHours(6),
                EventType.MEDICATION_INTAKE);
        event.setPatient(owner);
        event.setRef(9000003663L);

        Schedule schedule = new Schedule(
                new Medicine("Synthetic notification-only medicine", Presentation.PILLS));
        schedule.setId(event.getRef());
        schedule.setPatient(owner);
        schedule.setActiveMedId(9000003664L);
        schedule.addState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL);

        ActiveMedEntity external = new ActiveMedEntity();
        external.setId(9000003664L);
        external.setPatient(other);
        external.setType(ActiveMedType.NATIONAL_CODE);
        external.setDosage(new DosageEntity());

        assertFalse("Imported prescriptions belonging to another patient may not be shown",
                IntakeNotificationMgr.canRenderMedication(event, schedule, owner, external));
        external.setPatient(owner);
        assertTrue("Legitimate matching official association is still displayable",
                IntakeNotificationMgr.canRenderMedication(event, schedule, owner, external));
        assertFalse("Linked prescription must not render when its source disappears",
                IntakeNotificationMgr.canRenderMedication(event, schedule, owner, null));
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
