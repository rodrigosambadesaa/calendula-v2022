/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.adapters.items;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;

import es.usc.citius.servando.calendula.R;

import static org.junit.Assert.*;

/** No patient database fixtures: tests an intentionally reused, stale view. */
@RunWith(AndroidJUnit4.class)
public class ProviderCardRecyclingSmokeTest {

    @Test
    public void missingProviderRowCannotShowLastPatientsRecycledCardOrButtons() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View root = LayoutInflater.from(context).inflate(
                R.layout.active_medication_list_item, new FrameLayout(context), false);
        ActiveMedicationListItem item = new ActiveMedicationListItem(null);
        ActiveMedicationListItem.ActiveMedicationViewHolder holder = item.getViewHolder(root);

        holder.medName.setText("Previous patient's medication");
        holder.intakeSummary.setText("Take multiple daily doses");
        holder.intakeInstructions.setText("Sensitive prescription instructions");
        holder.intakeInstructions.setVisibility(View.VISIBLE);
        holder.startDate.setText("Previous start");
        holder.fromContainer.setVisibility(View.VISIBLE);
        holder.endDate.setText("Previous end");
        holder.toContainer.setVisibility(View.VISIBLE);
        holder.lastUpdateInfo.setText("Previous update");
        holder.lastUpdateInfo.setVisibility(View.VISIBLE);
        holder.reminderButton.setVisibility(View.VISIBLE);
        holder.reminderButton.setEnabled(true);
        holder.infoButton.setVisibility(View.VISIBLE);
        holder.infoButton.setEnabled(true);

        item.bindView(holder, Collections.emptyList());

        assertEquals(context.getString(R.string.active_med_details_unavailable),
                holder.medName.getText().toString());
        assertEquals("", holder.intakeSummary.getText().toString());
        assertEquals("", holder.intakeInstructions.getText().toString());
        assertEquals(View.GONE, holder.intakeInstructions.getVisibility());
        assertEquals(View.GONE, holder.fromContainer.getVisibility());
        assertEquals(View.GONE, holder.toContainer.getVisibility());
        assertEquals(View.GONE, holder.lastUpdateInfo.getVisibility());
        assertEquals(View.GONE, holder.reminderButton.getVisibility());
        assertEquals(View.GONE, holder.infoButton.getVisibility());
        assertFalse(holder.reminderButton.isEnabled());
        assertFalse(holder.infoButton.isEnabled());
    }
}
