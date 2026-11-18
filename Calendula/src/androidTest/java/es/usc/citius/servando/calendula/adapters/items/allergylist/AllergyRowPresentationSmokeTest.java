/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.adapters.items.allergylist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.allergies.AllergenType;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;

import static org.junit.Assert.*;

/** Disposable in-memory UI fixtures; no real patient data or database writes. */
@RunWith(AndroidJUnit4.class)
public class AllergyRowPresentationSmokeTest {

    @Test
    public void groupedAllergyRowCreatesAndBindsItsActualViewHolder() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PatientAllergen synthetic = new PatientAllergen(
                "Synthetic excipient", AllergenType.EXCIPIENT, "ci-ui-verified",
                new Patient());
        AllergyGroupSubItem item = new AllergyGroupSubItem(synthetic, context);
        View row = LayoutInflater.from(context).inflate(
                item.getLayoutRes(), new FrameLayout(context), false);
        AllergyGroupSubItem.ViewHolder holder = item.getViewHolder(row);

        assertNotNull("FastAdapter must never receive a null grouped row holder", holder);
        assertNotNull(holder.title);
        assertNotNull(holder.subtitle);
        item.bindView(holder, java.util.Collections.emptyList());
        assertEquals("Synthetic excipient",
                ((TextView) row.findViewById(R.id.text1)).getText().toString());
        assertEquals(context.getString(R.string.excipient),
                ((TextView) row.findViewById(R.id.text2)).getText().toString());
    }

    @Test
    public void incompleteImportedAllergyRowsKeepAVisibleReviewLabel() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String unavailable = context.getString(R.string.allergy_entry_unverified);

        PatientAllergen nameless = new PatientAllergen(
                null, AllergenType.EXCIPIENT, "ci-ui-nameless", new Patient());
        AllergyItem single = new AllergyItem(nameless, context);
        AllergyGroupSubItem grouped = new AllergyGroupSubItem(nameless, context);
        assertEquals(unavailable, single.getTitle());
        assertEquals(unavailable, grouped.getTitle());

        PatientAllergen typeless = new PatientAllergen(
                "Synthetic unknown type", null, "ci-ui-typeless", new Patient());
        AllergyItem unknownSingle = new AllergyItem(typeless, context);
        AllergyGroupSubItem unknownGrouped = new AllergyGroupSubItem(typeless, context);
        assertEquals("Synthetic unknown type", unknownSingle.getTitle());
        assertEquals("Synthetic unknown type", unknownGrouped.getTitle());

        View row = LayoutInflater.from(context).inflate(
                unknownGrouped.getLayoutRes(), new FrameLayout(context), false);
        AllergyGroupSubItem.ViewHolder holder = unknownGrouped.getViewHolder(row);
        assertNotNull(holder);
        unknownGrouped.bindView(holder, java.util.Collections.emptyList());
        assertEquals(unavailable,
                ((TextView) row.findViewById(R.id.text2)).getText().toString());
    }
}
