/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Synthetic owner tests plus actual device view inflation and accessibility. */
@RunWith(AndroidJUnit4.class)
public class DailyAgendaReviewWarningSmokeTest {

    @Test
    public void reviewBannerIsGenericAccessibleAndInitiallyHidden() {
        Context context = ApplicationProvider.getApplicationContext();
        View root = LayoutInflater.from(context).inflate(R.layout.fragment_daily_agenda, null);
        TextView warning = root.findViewById(R.id.agenda_medication_review_warning);
        assertNotNull(warning);
        assertTrue(warning.getVisibility() == View.GONE);
        assertTrue(warning.getText().length() > 0);
        assertFalse("Warning must never contain a synthetic patient identifier",
                warning.getText().toString().contains("CI-PATIENT"));
        warning.setVisibility(View.VISIBLE);
        assertTrue(warning.getVisibility() == View.VISIBLE);
        assertTrue(warning.getImportantForAccessibility()
                == View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        assertTrue(warning.getAccessibilityLiveRegion()
                == View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    @Test
    public void otherPatientsInvalidDoseNeverTriggersActiveWarning() {
        Patient a = new Patient();
        a.setId(710001L);
        Patient b = new Patient();
        b.setId(710002L);
        Medicine medicine = new Medicine("CI-PATIENT confidential synthetic dose",
                Presentation.PILLS);
        medicine.setPatient(a);
        Schedule schedule = new Schedule(medicine);
        schedule.setPatient(a);
        schedule.setId(810002L);
        EventInstance event = new EventInstance(DateTime.now(), EventType.MEDICATION_INTAKE);
        event.setId(910003L);
        event.setRef(schedule.getId());
        event.setPatient(a);
        event.addParam(EventInstance.PARAM_DOSE, "Infinity");
        assertTrue(DailyAgendaReviewPolicy.needsReview(event, schedule, a));
        assertFalse(DailyAgendaReviewPolicy.needsReview(event, schedule, b));
    }
}
