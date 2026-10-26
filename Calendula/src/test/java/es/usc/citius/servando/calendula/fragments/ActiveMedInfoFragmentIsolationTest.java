package es.usc.citius.servando.calendula.fragments;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageEntryVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;

import static org.junit.Assert.*;

/** Disposable model data only. No external patient or prescription records. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ActiveMedInfoFragmentIsolationTest {
    @Test
    public void dispensationCalendarMustBelongToOneFragmentNotAllPatients() throws Exception {
        Field field = ActiveMedInfoFragment.class.getDeclaredField("dispensationInfoStore");
        assertFalse("Clinical dispensations must not be shared across fragment instances",
                Modifier.isStatic(field.getModifiers()));
        Field calendarModel =
                ActiveMedInfoFragment.DispensationFragment.class.getDeclaredField("ownStore");
        assertFalse("The retained calendar widget must also have isolated clinical state",
                Modifier.isStatic(calendarModel.getModifiers()));
    }

    @Test
    public void reusedDetailViewMustRestoreDatesAndEraseStaleClinicalInstructions() {
        Context context = ApplicationProvider.getApplicationContext();
        View dateContainer = new View(context);
        TextView dateLabel = new TextView(context);
        TextView doseLabel = new TextView(context);

        ActiveMedInfoFragment.bindValidityDate(
                dateContainer, dateLabel, new DateTime(2026, 10, 26, 9, 0));
        assertEquals(View.VISIBLE, dateContainer.getVisibility());
        assertEquals("26/10/2026", dateLabel.getText().toString());

        ActiveMedInfoFragment.bindValidityDate(dateContainer, dateLabel, null);
        assertEquals(View.GONE, dateContainer.getVisibility());
        assertEquals("", dateLabel.getText().toString());

        ActiveMedInfoFragment.bindValidityDate(
                dateContainer, dateLabel, new DateTime(2026, 11, 2, 9, 0));
        assertEquals(View.VISIBLE, dateContainer.getVisibility());
        assertEquals("02/11/2026", dateLabel.getText().toString());

        ActiveMedInfoFragment.bindDosageText(doseLabel, "Synthetic dose 5 mL");
        assertEquals("Synthetic dose 5 mL", doseLabel.getText().toString());
        ActiveMedInfoFragment.bindDosageText(doseLabel, null);
        assertEquals(context.getString(R.string.dosage_string_not_available),
                doseLabel.getText().toString());
        ActiveMedInfoFragment.bindDosageText(doseLabel, "  ");
        assertEquals(context.getString(R.string.dosage_string_not_available),
                doseLabel.getText().toString());
    }

    @Test
    public void unavailableOrCorruptFirstProviderInstructionIsNotDisplayed() {
        assertNull(ActiveMedInfoFragment.firstPatientInstructionOrNull(null));
        assertNull(ActiveMedInfoFragment.firstPatientInstructionOrNull(Collections.emptyList()));
        assertNull(ActiveMedInfoFragment.firstPatientInstructionOrNull(
                Collections.singletonList(null)));
        DosageEntryVO empty = new DosageEntryVO(new DosageEntryEntity());
        assertNull(ActiveMedInfoFragment.firstPatientInstructionOrNull(
                Collections.singletonList(empty)));
        empty.setPatientInstruction("  ");
        assertNull(ActiveMedInfoFragment.firstPatientInstructionOrNull(
                Collections.singletonList(empty)));
        DosageEntryVO valid = new DosageEntryVO(new DosageEntryEntity());
        valid.setPatientInstruction("Synthetic instruction");
        assertEquals("Synthetic instruction",
                ActiveMedInfoFragment.firstPatientInstructionOrNull(
                        Collections.singletonList(valid)));
        assertNull("Never fall back to later entries when the first is corrupt",
                ActiveMedInfoFragment.firstPatientInstructionOrNull(
                        Arrays.asList(null, valid)));
    }
}
