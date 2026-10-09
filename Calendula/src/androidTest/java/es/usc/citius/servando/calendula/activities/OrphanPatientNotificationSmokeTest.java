/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An orphan medication reminder must not crash the OS alarm worker or
 * construct a notification action referring to a nonexistent patient.
 * No real patient data is used and no notification should be posted.
 */
@RunWith(AndroidJUnit4.class)
public class OrphanPatientNotificationSmokeTest {

    @Test
    public void missingPatientDoesNotCrashMedicationNotificationRendering() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EventReminder orphan = new EventReminder(
                DateTime.now().plusHours(9), EventType.MEDICATION_INTAKE);
        orphan.setNextTime(orphan.getDateTime());
        assertFalse(IntakeNotificationMgr.hasNotificationPatient(orphan));
        // Guard executes before login, DB queries, images and Android UI
        // interactions. Do not emit an unassigned medication action.
        IntakeNotificationMgr.notify(context, orphan, false);
        IntakeNotificationMgr.notify(context, orphan, true);
    }

    @Test
    public void unpersistedPatientCannotAuthorizeMedicationActions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EventReminder orphan = new EventReminder(
                DateTime.now().plusHours(10), EventType.MEDICATION_INTAKE);
        Patient synthetic = new Patient();
        synthetic.setName("Synthetic unpersisted patient");
        orphan.setPatient(synthetic);

        assertFalse(IntakeNotificationMgr.hasNotificationPatient(orphan));
        IntakeNotificationMgr.notify(context, orphan, false);

        // A stored identity passes the identity-only guard; the full
        // medication schedule and dose are separately validated in #540.
        synthetic.setId(9000003999L);
        assertTrue(IntakeNotificationMgr.hasNotificationPatient(orphan));
    }
}
