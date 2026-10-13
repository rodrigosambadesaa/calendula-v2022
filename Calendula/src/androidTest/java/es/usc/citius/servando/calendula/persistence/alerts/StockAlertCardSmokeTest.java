/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence.alerts;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;

import static org.junit.Assert.*;

/** Real Android rendering and synthetic SQLite fixtures only. */
@RunWith(AndroidJUnit4.class)
public class StockAlertCardSmokeTest {

    @Test
    public void reusedCardRendersFractionalZeroAndMissingStockSafely() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient owner = new Patient();
        owner.setCode("ci-stock-card-" + System.nanoTime());
        owner.setName("Synthetic stock card patient");
        Medicine medicine = new Medicine("Synthetic stock card medicine", Presentation.PILLS);
        medicine.setStock(0.5f);
        StockRunningOutAlert.StockAlertViewProvider provider =
                new StockRunningOutAlert.StockAlertViewProvider();
        StockRunningOutAlert.StockAlertViewProvider.StockAlertViewHolder holder = null;

        try {
            assertEquals("Create synthetic stock patient", 1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals("Create synthetic stock medicine", 1, DB.medicines().create(medicine));
            StockRunningOutAlert alert = new StockRunningOutAlert(medicine, LocalDate.now());
            holder = (StockRunningOutAlert.StockAlertViewProvider.StockAlertViewHolder)
                    provider.onCreateViewHolder(new FrameLayout(context));

            provider.onBindViewHolder(holder, alert);
            assertEquals(View.VISIBLE, holder.description.getVisibility());
            assertTrue("Fractional quantity must not be rounded to depleted",
                    holder.description.getText().toString().contains("0.5"));

            medicine.setStock(0f);
            assertEquals("Write depleted stock fixture", 1, DB.medicines().update(medicine));
            provider.onBindViewHolder(holder, alert);
            assertEquals(context.getString(R.string.stock_depleted),
                    holder.title.getText().toString());
            assertEquals(View.GONE, holder.description.getVisibility());

            medicine.setStock(2f);
            assertEquals("Write replenished stock fixture", 1, DB.medicines().update(medicine));
            provider.onBindViewHolder(holder, alert);
            assertEquals("A recycled zero-stock holder must restore its description",
                    View.VISIBLE, holder.description.getVisibility());
            assertTrue(holder.description.getText().toString().contains("2"));

            medicine.setStock(null);
            assertEquals("Write null-stock legacy fixture", 1, DB.medicines().update(medicine));
            provider.onBindViewHolder(holder, alert);
            assertEquals("Missing legacy quantity is unknown, not depleted",
                    context.getString(R.string.stock_estimate_unavailable),
                    holder.duration.getText().toString());
            assertEquals(View.GONE, holder.description.getVisibility());
            assertEquals(View.VISIBLE, holder.manageStockBtn.getVisibility());

            StockRunningOutAlert orphan = new StockRunningOutAlert();
            orphan.setPatient(owner);
            provider.onBindViewHolder(holder, orphan);
            assertEquals(View.GONE, holder.manageStockBtn.getVisibility());
            assertEquals(context.getString(R.string.stock_estimate_unavailable),
                    holder.duration.getText().toString());
        } finally {
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }
}
