/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import com.j256.ormlite.dao.Dao;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A missing active patient is not permission to read anonymous medications.
 * These tests deliberately use a mocked DAO, never clinical SQLite data.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PatientScopedIntakeReadGuardTest {

    @SuppressWarnings("unchecked")
    @Test
    public void noPatientOrUnsavedPatientCannotQueryAnyIntakeRows() throws Exception {
        DatabaseHelper helper = mock(DatabaseHelper.class);
        Dao<EventInstance, Long> raw = (Dao<EventInstance, Long>) mock(Dao.class);
        when(helper.getEventInstanceDao()).thenReturn(raw);
        EventInstanceDao events = new EventInstanceDao(helper);
        DateTime time = new DateTime(2026, 10, 11, 10, 30);
        EventType type = EventType.MEDICATION_INTAKE;

        Patient unsaved = new Patient();
        Patient zero = new Patient();
        zero.setId(0L);
        Patient saved = new Patient();
        saved.setId(900100L);
        for (Patient invalid : new Patient[]{null, unsaved, zero}) {
            assertTrue(events.find(type, time, invalid).isEmpty());
            assertTrue(events.findPending(type, time, invalid).isEmpty());
            assertFalse(events.exists(type, time, invalid, false));
            assertFalse(events.existsPending(type, time, invalid));
        }
        assertTrue(events.find(null, time, saved).isEmpty());
        assertTrue(events.find(type, null, saved).isEmpty());
        assertTrue(events.findPending(null, time, saved).isEmpty());
        assertTrue(events.findPending(type, null, saved).isEmpty());
        assertFalse(events.exists(null, time, saved, false));
        assertFalse(events.exists(type, null, saved, false));
        assertFalse(events.existsPending(null, time, saved));
        assertFalse(events.existsPending(type, null, saved));

        verifyNoInteractions(raw);
    }
}
