/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import android.view.View;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import es.usc.citius.servando.calendula.util.DailyAgendaItemStub;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Synthetic view state only; does not query or modify any patient data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DailyAgendaRefreshFailureTest {
    @Test
    public void failedRebuildHidesOldPatientRowsAndShowsGenericWarning() {
        List<DailyAgendaItemStub> previousItems = new ArrayList<>();
        previousItems.add(null); // represents an old private agenda row
        View agenda = mock(View.class);
        View empty = mock(View.class);
        TextView banner = mock(TextView.class);

        DailyAgendaFragment.hideStaleAgendaOnRefreshError(
                previousItems, agenda, empty, banner);

        assertTrue(previousItems.isEmpty());
        verify(agenda).setVisibility(View.GONE);
        verify(empty).setVisibility(View.GONE);
        verify(banner).setVisibility(View.VISIBLE);
    }

    @Test
    public void missingFragmentViewsAreSafeDuringDestroyAndRestore() {
        List<DailyAgendaItemStub> previousItems = new ArrayList<>();
        previousItems.add(null);
        DailyAgendaFragment.hideStaleAgendaOnRefreshError(previousItems, null, null, null);
        assertTrue(previousItems.isEmpty());
    }
}
