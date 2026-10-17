/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import es.usc.citius.servando.calendula.util.DailyAgendaItemStub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Tests real Android view visibility without accessing patient records. */
@RunWith(AndroidJUnit4.class)
public class DailyAgendaRefreshFailureSmokeTest {
    @Test
    public void oldAgendaRowsAreNotVisibleAfterFailedRebuild() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
            View agenda = new View(ctx);
            View emptyPlaceholder = new View(ctx);
            TextView warning = new TextView(ctx);
            List<DailyAgendaItemStub> previousRows = new ArrayList<>();
            previousRows.add(null);
            DailyAgendaFragment.hideStaleAgendaOnRefreshError(
                    previousRows, agenda, emptyPlaceholder, warning);
            assertTrue(previousRows.isEmpty());
            assertEquals(View.GONE, agenda.getVisibility());
            assertEquals(View.GONE, emptyPlaceholder.getVisibility());
            assertEquals(View.VISIBLE, warning.getVisibility());
        });
    }
}
