/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence.alerts;

import android.content.Context;
import android.widget.FrameLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import java.util.Arrays;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.allergies.AllergenVO;

import static org.junit.Assert.*;

/** UI-only test: no database or real patient clinical information involved. */
@RunWith(AndroidJUnit4.class)
public class AllergyAlertCardSmokeTest {
    @Test
    public void recycledHighAlertShowsFallbackForMissingOrPartialAllergenDetails() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AllergyPatientAlert.AllergyAlertViewProvider provider =
                new AllergyPatientAlert.AllergyAlertViewProvider();
        AllergyPatientAlert.AllergyAlertViewProvider.AllergyAlertViewHolder holder =
                (AllergyPatientAlert.AllergyAlertViewProvider.AllergyAlertViewHolder)
                        provider.onCreateViewHolder(new FrameLayout(context));

        AllergyPatientAlert noDetails = new AllergyPatientAlert();
        provider.onBindViewHolder(holder, noDetails);
        assertNotNull("Never hide HIGH allergy warning icon", holder.alertIcon.getDrawable());
        assertEquals(context.getString(R.string.title_alert_allergy),
                holder.title.getText().toString());
        assertEquals(context.getString(R.string.allergy_details_unavailable),
                holder.allergens.getText().toString());

        AllergyPatientAlert complete = new AllergyPatientAlert();
        complete.setDetails(new AllergyPatientAlert.AllergyAlertInfo(
                Collections.singletonList(new AllergenVO(null, "Known synthetic allergen", "dummy"))));
        provider.onBindViewHolder(holder, complete);
        assertTrue("A valid rebinding must show its real allergen details",
                holder.allergens.getText().toString().contains("Known synthetic allergen"));

        AllergyPatientAlert markupName = new AllergyPatientAlert();
        markupName.setDetails(new AllergyPatientAlert.AllergyAlertInfo(
                Collections.singletonList(new AllergenVO(
                        null, "Synthetic <milk> & nuts", "dummy-escaped"))));
        provider.onBindViewHolder(holder, markupName);
        assertTrue("Allergen labels must be literal, not interpreted as HTML",
                holder.allergens.getText().toString().contains(
                        "Synthetic <milk> & nuts"));

        AllergyPatientAlert partial = new AllergyPatientAlert();
        partial.setDetails(new AllergyPatientAlert.AllergyAlertInfo(
                Arrays.asList(new AllergenVO(null, "Known synthetic allergen", "dummy"), null)));
        provider.onBindViewHolder(holder, partial);
        assertEquals("A partial list must not masquerade as complete information",
                context.getString(R.string.allergy_details_unavailable),
                holder.allergens.getText().toString());
        assertNotNull(holder.alertIcon.getDrawable());
    }
}
