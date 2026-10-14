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

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.alerts.StockRunningOutAlert;

import static org.junit.Assert.*;

/**
 * Reuse a real medicine row's view holder across a valid warning, no warning
 * and a corrupt cross-owner clinical alert, without touching real patient data.
 */
@RunWith(AndroidJUnit4.class)
public class MedicineAlertBadgeSmokeTest {
    @Test
    public void recycledBadgeClearsPriorAlertAndRejectsCrossPatientAlertRows()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String seed = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-badge-owner-" + seed);
        owner.setName("Synthetic medicine badge owner");
        Patient foreign = new Patient();
        foreign.setCode("ci-badge-foreign-" + seed);
        foreign.setName("Synthetic unrelated patient");
        Medicine warned = new Medicine("Synthetic warned medicine", Presentation.PILLS);
        Medicine clean = new Medicine("Synthetic clean medicine", Presentation.PILLS);
        StockRunningOutAlert alert = null;
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(foreign));
            warned.setPatient(owner);
            clean.setPatient(owner);
            assertEquals(1, DB.medicines().create(warned));
            assertEquals(1, DB.medicines().create(clean));
            alert = new StockRunningOutAlert(warned, LocalDate.now());
            assertEquals(1, DB.alerts().create(alert));

            View view = LayoutInflater.from(context).inflate(
                    R.layout.medicines_list_item, new FrameLayout(context), false);
            MedicineItem.MedicineViewHolder holder =
                    new MedicineItem(warned).getViewHolder(view);

            new MedicineItem(warned).bindView(holder, Collections.emptyList());
            assertEquals(View.VISIBLE, holder.alertIcon.getVisibility());
            assertNotNull(holder.alertIcon.getDrawable());

            new MedicineItem(clean).bindView(holder, Collections.emptyList());
            assertEquals("A recycled card without alerts must have no icon",
                    View.GONE, holder.alertIcon.getVisibility());
            assertNull(holder.alertIcon.getDrawable());

            // Simulate damaged imported SQLite ownership. The medicine is
            // still owned by A, but its alert now belongs to B.
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE PatientAlerts SET Patient = ? WHERE _id = ?",
                    new Object[]{foreign.getId(), alert.id()});
            new MedicineItem(warned).bindView(holder, Collections.emptyList());
            assertEquals("Unreadable alerts must show a non-clinical warning",
                    View.VISIBLE, holder.alertIcon.getVisibility());
            assertEquals(context.getString(R.string.medicine_alerts_unavailable),
                    holder.alertIcon.getContentDescription().toString());

            new MedicineItem(clean).bindView(holder, Collections.emptyList());
            assertEquals(View.GONE, holder.alertIcon.getVisibility());
            assertNull(holder.alertIcon.getContentDescription());
        } finally {
            if (alert != null && alert.id() != null
                    && DB.alerts().findById(alert.id()) != null) {
                DB.alerts().deleteById(alert.id());
            }
            for (Medicine medicine : new Medicine[]{warned, clean}) {
                if (medicine.getId() != null
                        && DB.medicines().findById(medicine.getId()) != null) {
                    DB.medicines().deleteById(medicine.getId());
                }
            }
            for (Patient p : new Patient[]{foreign, owner}) {
                if (p.getId() != null && DB.patients().findById(p.getId()) != null) {
                    DB.patients().deleteById(p.getId());
                }
            }
        }
    }
}
