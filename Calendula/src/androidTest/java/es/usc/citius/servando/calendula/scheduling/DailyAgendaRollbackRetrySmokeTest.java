/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Actual SQLite rollback, SharedPreferences and startup retry on API 23+.
 * Uses a disposable orphan reminder, no real patient, schedule or medication.
 * Only execute on an isolated Android emulator test database.
 */
@RunWith(AndroidJUnit4.class)
public class DailyAgendaRollbackRetrySmokeTest {

    @Test
    public void failedSqlitePruneKeepsOldMarkerAndRetryCompletesDay() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreferenceUtils.init(context);
        final String key = PreferenceKeys.AGENDA_LAST_UPDATED.key();
        final String original = PreferenceUtils.getString(
                PreferenceKeys.AGENDA_LAST_UPDATED, null);
        final String staleMarker = "19990101";
        final String today = LocalDate.now().toString(ISODateTimeFormat.basicDate());

        // The daily prune removes dates older than yesterday's midnight.
        DateTime oldTime = DateTime.now().minusDays(5).withMillisOfSecond(0);
        EventReminder synthetic = new EventReminder(oldTime, EventType.MEDICATION_INTAKE);
        synthetic.setNextTime(oldTime);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_daily_agenda_rollback";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            DB.eventReminders().save(synthetic);
            assertNotNull(synthetic.getId());
            assertNotNull(DB.eventReminders().findById(synthetic.getId()));
            assertTrue(PreferenceUtils.edit().putString(key, staleMarker).commit());

            // Abort at SQLite DELETE; an outer transaction cannot commit.
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventReminders WHEN OLD._id = "
                    + synthetic.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic daily prune abort'); END;");

            Agenda.instance().onDailyUpdate(context);
            assertNotNull("Failed transaction must roll back the expired row",
                    DB.eventReminders().findById(synthetic.getId()));
            assertEquals("A failed transaction must not advance the durable daily marker",
                    staleMarker, PreferenceUtils.getString(
                            PreferenceKeys.AGENDA_LAST_UPDATED, null));
            assertTrue("A subsequent launch must retry failed agenda generation",
                    Agenda.shouldRefreshAgenda(LocalDate.now(),
                            PreferenceUtils.getString(
                                    PreferenceKeys.AGENDA_LAST_UPDATED, null)));

            // Remove only our synthetic failure: the normal daily path must
            // then prune exactly the old row and commit today's marker.
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            Agenda.instance().onDailyUpdate(context);
            assertNull("Retry must prune the obsolete reminder",
                    DB.eventReminders().findById(synthetic.getId()));
            assertEquals("Retry must only mark the day after all post-commit work",
                    today, PreferenceUtils.getString(
                            PreferenceKeys.AGENDA_LAST_UPDATED, null));
            assertFalse("Successful retry must not regenerate agenda repeatedly",
                    Agenda.shouldRefreshAgenda(LocalDate.now(),
                            PreferenceUtils.getString(
                                    PreferenceKeys.AGENDA_LAST_UPDATED, null)));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (synthetic.getId() != null) {
                if (DB.eventReminders().findById(synthetic.getId()) != null) {
                    DB.eventReminders().remove(synthetic);
                }
                Agenda.instance().cancelAlarm(context, synthetic);
            }
            if (original == null) {
                assertTrue(PreferenceUtils.edit().remove(key).commit());
            } else {
                assertTrue(PreferenceUtils.edit().putString(key, original).commit());
            }
        }
    }
}
