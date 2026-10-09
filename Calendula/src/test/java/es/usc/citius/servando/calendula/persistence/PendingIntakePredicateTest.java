/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Shared by bulk-check and confirm-screen controls. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PendingIntakePredicateTest {
    @Test
    public void onlyUncompletedUncancelledIntakesMayBeChecked() {
        EventInstance intake = new EventInstance(DateTime.now().plusHours(3),
                EventType.MEDICATION_INTAKE);
        assertFalse(ScheduleUtils.isPendingIntake(null));
        assertTrue(ScheduleUtils.isPendingIntake(intake));
        intake.setCancelled(true);
        assertFalse("Cancelled medication must never be marked taken",
                ScheduleUtils.isPendingIntake(intake));
        intake.setCancelled(false);
        intake.setCompleted(true);
        assertFalse("Completed medication must not be confirmed again",
                ScheduleUtils.isPendingIntake(intake));
        intake.setCancelled(true);
        assertFalse("Conflicting legacy flags must remain ineligible",
                ScheduleUtils.isPendingIntake(intake));
    }
}
