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

/** Tests Android stock cards with independent, disposable medicine rows. */
@RunWith(AndroidJUnit4.class)
public class StockAlertCardSmokeTest {

    @Test
    public void reusedCardRendersFractionalZeroAndMissingStockSafely() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient owner = new Patient();
        owner.setCode("ci-stock-card-" + System.nanoTime());
        owner.setName("Synthetic stock card patient");
        // These independent rows deliberately avoid updating a medicine
        // while a background agenda/alert observer might be reading it.
        Medicine fraction = new Medicine("Synthetic fractional stock", Presentation.PILLS);
        fraction.setStock(0.5f);
        Medicine depleted = new Medicine("Synthetic depleted stock", Presentation.PILLS);
        depleted.setStock(0f);
        Medicine replenished = new Medicine("Synthetic replenished stock", Presentation.PILLS);
        replenished.setStock(2f);
        Medicine missing = new Medicine("Synthetic missing stock", Presentation.PILLS);
        missing.setStock(null);
        Medicine[] fixtures = {fraction, depleted, replenished, missing};
        StockRunningOutAlert.StockAlertViewProvider provider =
                new StockRunningOutAlert.StockAlertViewProvider();

        try {
            assertEquals("Create synthetic stock patient", 1, DB.patients().create(owner));
            for (Medicine medicine : fixtures) {
                medicine.setPatient(owner);
                assertEquals("Persist independent synthetic medicine: " + medicine.getName(),
                        1, DB.medicines().create(medicine));
                assertNotNull("Fixture must have an ID", medicine.getId());
            }

            StockRunningOutAlert.StockAlertViewProvider.StockAlertViewHolder holder =
                    (StockRunningOutAlert.StockAlertViewProvider.StockAlertViewHolder)
                            provider.onCreateViewHolder(new FrameLayout(context));

            provider.onBindViewHolder(holder,
                    new StockRunningOutAlert(fraction, LocalDate.now()));
            assertEquals(View.VISIBLE, holder.description.getVisibility());
            assertTrue("Fractional quantity must not be rounded to depleted",
                    holder.description.getText().toString().contains("0.5"));

            provider.onBindViewHolder(holder,
                    new StockRunningOutAlert(depleted, LocalDate.now()));
            assertEquals(context.getString(R.string.stock_depleted),
                    holder.title.getText().toString());
            assertEquals(View.GONE, holder.description.getVisibility());

            provider.onBindViewHolder(holder,
                    new StockRunningOutAlert(replenished, LocalDate.now()));
            assertEquals("Recycled holder must restore its description",
                    View.VISIBLE, holder.description.getVisibility());
            assertTrue(holder.description.getText().toString().contains("2"));

            provider.onBindViewHolder(holder,
                    new StockRunningOutAlert(missing, LocalDate.now()));
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
            for (Medicine medicine : fixtures) {
                if (medicine.getId() != null
                        && DB.medicines().findById(medicine.getId()) != null) {
                    DB.medicines().remove(medicine);
                }
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }
}
