/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/** Synthetic owner keys only: never group two restored patients by row ID. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DailyAgendaOwnerGroupingTest {

    private static Patient owner(long id, String code) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    @Test
    public void sameNumericIdButDifferentStableCodeMakesDifferentAgendaRows() {
        Patient a = owner(710001L, "ci-owner-before-restore");
        Patient b = owner(710001L, "ci-owner-after-restore");
        assertNotEquals(DailyAgendaFragment.stableAgendaOwnerKey(a),
                DailyAgendaFragment.stableAgendaOwnerKey(b));
        assertEquals(DailyAgendaFragment.stableAgendaOwnerKey(a),
                DailyAgendaFragment.stableAgendaOwnerKey(
                        owner(710001L, "ci-owner-before-restore")));
        assertNotEquals(DailyAgendaFragment.stableAgendaOwnerKey(a),
                DailyAgendaFragment.stableAgendaOwnerKey(
                        owner(710002L, "ci-owner-before-restore")));
    }

    @Test
    public void legacyUnidentifiedRowsCannotAcquireAClinicalGroupingKey() {
        assertNull(DailyAgendaFragment.stableAgendaOwnerKey(null));
        assertNull(DailyAgendaFragment.stableAgendaOwnerKey(
                owner(710001L, null)));
        assertNull(DailyAgendaFragment.stableAgendaOwnerKey(
                owner(710001L, " ")));
        assertNull(DailyAgendaFragment.stableAgendaOwnerKey(
                owner(0L, "ci-stable")));
        assertNull(DailyAgendaFragment.stableAgendaOwnerKey(
                owner(-1L, "ci-stable")));
    }
}
