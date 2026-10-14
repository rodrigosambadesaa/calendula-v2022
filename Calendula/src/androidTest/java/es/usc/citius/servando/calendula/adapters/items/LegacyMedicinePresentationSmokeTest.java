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
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;

import static org.junit.Assert.*;

/** Real Android widget regression for incomplete pre-migration medicine rows. */
@RunWith(AndroidJUnit4.class)
public class LegacyMedicinePresentationSmokeTest {
    @Test
    public void nullPresentationStillRendersMedicineWithUnknownPresentationIcon()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient patient = new Patient();
        patient.setCode("ci-legacy-presentation-" + System.nanoTime());
        patient.setName("Synthetic legacy presentation owner");
        Medicine legacy = new Medicine("Synthetic missing presentation", null);
        legacy.setStock(1.5f);
        try {
            assertEquals(1, DB.patients().create(patient));
            legacy.setPatient(patient);
            assertEquals(1, DB.medicines().create(legacy));

            View root = LayoutInflater.from(context).inflate(
                    R.layout.medicines_list_item, new FrameLayout(context), false);
            MedicineItem item = new MedicineItem(legacy);
            MedicineItem.MedicineViewHolder holder = item.getViewHolder(root);
            item.bindView(holder, Collections.emptyList());

            assertEquals("Missing presentation should be displayed, not crash",
                    legacy.getName(), holder.name.getText().toString());
            assertNotNull(holder.icon.getDrawable());
            assertEquals(Presentation.UNKNOWN.getName(context.getResources()),
                    holder.icon.getContentDescription().toString());

            item.unbindView(holder);
            assertNull(holder.icon.getDrawable());
            assertNull(holder.icon.getContentDescription());
        } finally {
            if (legacy.getId() != null && DB.medicines().findById(legacy.getId()) != null)
                DB.medicines().deleteById(legacy.getId());
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null)
                DB.patients().deleteById(patient.getId());
        }
    }
}
