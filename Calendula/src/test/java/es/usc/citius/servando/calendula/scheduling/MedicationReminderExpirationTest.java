/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A postponed dose must not be treated as lost solely because its original time is old. */
public class MedicationReminderExpirationTest {

    private static final DateTime DELIVERY = new DateTime(
            2026, 10, 13, 16, 0, DateTimeZone.forID("Europe/Madrid"));

    @Test
    public void explicitlySnoozedOldIntakeKeepsActionsWhenRescheduledAlarmIsOnTime() {
        EventReminder reminder = new EventReminder(
                DELIVERY.minusDays(3), EventType.MEDICATION_INTAKE);
        reminder.setNextTime(DELIVERY.minusMinutes(1));

        assertFalse(MedicationIntakeReceiver.isScheduledDeliveryLost(
                reminder, DELIVERY, 15));
    }

    @Test
    public void autoRepeatedOldIntakeAlsoUsesTheLastPersistedDelivery() {
        EventReminder reminder = new EventReminder(
                DELIVERY.minusDays(5), EventType.MEDICATION_INTAKE);
        reminder.setAutoRepeat(true);
        reminder.setNextTime(DELIVERY.plusMinutes(14));

        assertFalse(MedicationIntakeReceiver.isScheduledDeliveryLost(
                reminder, DELIVERY, 15));
    }

    @Test
    public void lateScheduledAlarmIsLostEvenIfOriginalTimeIsRecent() {
        EventReminder reminder = new EventReminder(
                DELIVERY.plusDays(1), EventType.MEDICATION_INTAKE);
        reminder.setNextTime(DELIVERY.minusMinutes(16));

        assertTrue(MedicationIntakeReceiver.isScheduledDeliveryLost(
                reminder, DELIVERY, 15));
    }

    @Test
    public void boundaryIsInclusiveAndNullOrMalformedReminderFailsClosed() {
        EventReminder reminder = new EventReminder(
                DELIVERY.minusDays(2), EventType.MEDICATION_INTAKE);
        reminder.setNextTime(DELIVERY.minusMinutes(15));
        assertFalse(MedicationIntakeReceiver.isScheduledDeliveryLost(
                reminder, DELIVERY, 15));

        reminder.setNextTime(null);
        assertTrue(MedicationIntakeReceiver.isScheduledDeliveryLost(
                reminder, DELIVERY, 15));
        assertTrue(MedicationIntakeReceiver.isScheduledDeliveryLost(
                null, DELIVERY, 15));
        reminder.setNextTime(DELIVERY);
        assertTrue(MedicationIntakeReceiver.isScheduledDeliveryLost(
                reminder, null, 15));
        assertTrue(MedicationIntakeReceiver.isScheduledDeliveryLost(
                reminder, DELIVERY, -1));
    }
}
