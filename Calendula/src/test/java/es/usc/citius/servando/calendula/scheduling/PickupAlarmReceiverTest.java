/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Locale;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.PickupNotification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PickupAlarmReceiverTest {
    private static Intent pickupAction() {
        return new Intent().putExtra(CalendulaApp.INTENT_EXTRA_ACTION,
                CalendulaApp.ACTION_CHECK_PICKUPS_ALARM);
    }

    @Test
    public void onlyTheExplicitPickupActionCanPostANotice() {
        assertFalse(PickupAlarmReceiver.isPickupCheck(null));
        assertFalse(PickupAlarmReceiver.isPickupCheck(new Intent()));
        assertFalse(PickupAlarmReceiver.isPickupCheck(new Intent().putExtra(
                CalendulaApp.INTENT_EXTRA_ACTION, -1)));
        assertTrue(PickupAlarmReceiver.isPickupCheck(pickupAction()));

        Context context = RuntimeEnvironment.getApplication();
        PickupAlarmReceiver receiver = new PickupAlarmReceiver();
        boolean before = CalendulaApp.disableReceivers;
        CalendulaApp.disableReceivers = false;
        try (MockedStatic<PickupNotification> notifications =
                     org.mockito.Mockito.mockStatic(PickupNotification.class)) {
            receiver.onReceive(context, null);
            receiver.onReceive(context, new Intent());
            notifications.verifyNoInteractions();

            receiver.onReceive(context, pickupAction());
            notifications.verify(() -> PickupNotification.notify(
                    org.mockito.ArgumentMatchers.eq(context),
                    org.mockito.ArgumentMatchers.eq(
                            context.getString(R.string.pickup_alarm_title)),
                    org.mockito.ArgumentMatchers.eq(
                            context.getString(R.string.pickup_alarm_description)),
                    any(Intent.class)));
        } finally {
            CalendulaApp.disableReceivers = before;
        }
    }

    @Test
    public void pickupTextIsAvailableInEnglishSpanishAndGalician() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("Medication pickup reminder",
                localized(context, "en").getString(R.string.pickup_alarm_title));
        assertEquals("Recordatorio de recogida de medicamentos",
                localized(context, "es").getString(R.string.pickup_alarm_title));
        assertEquals("Recordatorio de recollida de medicamentos",
                localized(context, "gl").getString(R.string.pickup_alarm_title));
        assertTrue(localized(context, "gl")
                .getString(R.string.pickup_alarm_description).contains("calendario"));
    }

    private static Context localized(Context context, String language) {
        Configuration configuration = new Configuration(
                context.getResources().getConfiguration());
        configuration.setLocale(new Locale(language));
        return context.createConfigurationContext(configuration);
    }
}
