/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Disposable SQLite patient identity only. Never post real health data.
 * A cached agenda row must not survive its owner's replacement after restore.
 */
@RunWith(AndroidJUnit4.class)
public class DailyAgendaOwnerRestoreSmokeTest {
    @Test
    public void cachedAgendaOwnerRequiresCurrentMatchingSqliteCode() throws Exception {
        assertTrue(DB.initialized);
        Patient live = new Patient();
        live.setName("Synthetic agenda cache patient");
        live.setCode("ci-agenda-cache-" + System.nanoTime());
        try {
            assertEquals(1, DB.patients().create(live));
            assertNotNull(live.getId());
            assertTrue(DailyAgendaRecyclerAdapter.isPersistedAgendaOwner(live));

            Patient previouslyShown = new Patient();
            previouslyShown.setId(live.getId());
            previouslyShown.setCode("ci-previous-patient");
            assertFalse("A restored/reused numeric ID is not clinical UI authority",
                    DailyAgendaRecyclerAdapter.isPersistedAgendaOwner(previouslyShown));

            previouslyShown.setCode(null);
            assertFalse("Unidentified clinical row cannot display cached medicine",
                    DailyAgendaRecyclerAdapter.isPersistedAgendaOwner(previouslyShown));

            previouslyShown.setCode(live.getCode());
            assertTrue("Stable matching owner remains displayable after DB reload",
                    DailyAgendaRecyclerAdapter.isPersistedAgendaOwner(previouslyShown));

            Patient deleted = new Patient();
            deleted.setId(Long.MAX_VALUE);
            deleted.setCode("ci-absent-owner");
            assertFalse("A missing SQLite owner cannot render a stale agenda",
                    DailyAgendaRecyclerAdapter.isPersistedAgendaOwner(deleted));
        } finally {
            if (live.getId() != null && DB.patients().findById(live.getId()) != null) {
                DB.patients().remove(live);
            }
        }
    }
}
