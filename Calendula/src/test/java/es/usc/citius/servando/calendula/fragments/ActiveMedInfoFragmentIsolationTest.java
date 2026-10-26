package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;

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
