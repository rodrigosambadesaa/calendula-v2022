/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

import es.usc.citius.servando.calendula.activities.IntakeNotificationMgr;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.EventReminderDao;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Synthetic deterministic check-to-cancel interleaving, no real clinical data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PostCommitReminderCancellationRaceTest {

    private static EventReminder reminder(long id) {
        DateTime due = DateTime.now().plusHours(5);
        EventReminder instance = new EventReminder(due, EventType.MEDICATION_INTAKE);
        instance.setId(id);
        instance.setNextTime(due);
        return instance;
    }

    private static PendingIntent token(Context context, EventReminder reminder, int flags) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(flags));
    }

    @Test
    public void patientEraseCannotCancelReplacementInsertedAfterLookup() {
        exerciseInterleaving(true);
    }

    @Test
    public void dailyPruneCannotCancelReplacementInsertedAfterLookup() {
        exerciseInterleaving(false);
    }

    private void exerciseInterleaving(boolean patientErase) {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder retired = reminder(patientErase ? 9000005501L : 9000005502L);
        EventReminder successor = reminder(retired.getId());
        EventReminderDao reminders = mock(EventReminderDao.class);
        PendingIntent original = token(context, retired, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent[] replacement = new PendingIntent[1];
        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<IntakeNotificationMgr> notifications =
                     mockStatic(IntakeNotificationMgr.class)) {
            db.when(DB::eventReminders).thenReturn(reminders);
            when(reminders.findById(retired.getId())).thenAnswer(call -> {
                // A new patient re-arms a reused numeric ID *during* the old
                // cleanup's SQL read. The cleanup resumes afterwards.
                replacement[0] = token(context, successor, PendingIntent.FLAG_UPDATE_CURRENT);
                return null;
            });
            if (patientErase) {
                Agenda.instance().cancelDeletedPatientReminderTokens(
                        context, Collections.singletonList(retired));
            } else {
                assertTrue(Agenda.instance().retirePrunedReminderToken(context, retired));
            }

            assertNull("Old captured alarm instance must be retired",
                    token(context, retired, PendingIntent.FLAG_NO_CREATE));
            assertNotNull("Replacement alarm must survive the old cancellation",
                    token(context, successor, PendingIntent.FLAG_NO_CREATE));
            assertNotNull(replacement[0]);
        } finally {
            original.cancel();
            if (replacement[0] != null) replacement[0].cancel();
        }
    }
}
