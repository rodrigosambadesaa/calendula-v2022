/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MedicationUpdateRevisionTest {
    @Test
    public void missingOrInvalidProviderRevisionMustNeverBecomeClinicalUpdateHash() {
        assertFalse(UpdateMedicationHelper.isUsableProviderRevision(null));
        assertFalse(UpdateMedicationHelper.isUsableProviderRevision(""));
        assertFalse(UpdateMedicationHelper.isUsableProviderRevision("  "));
        assertFalse(UpdateMedicationHelper.isUsableProviderRevision("null"));
        assertFalse(UpdateMedicationHelper.isUsableProviderRevision("NULL"));
        assertFalse(UpdateMedicationHelper.isUsableProviderRevision("\r\nmalformed"));
        assertFalse(UpdateMedicationHelper.isUsableProviderRevision("revision\u0000malformed"));
    }

    @Test
    public void existingOpaqueProviderRevisionsRemainAcceptedWithoutNormalization() {
        assertTrue(UpdateMedicationHelper.isUsableProviderRevision(
                "2026-10-14_0df34b5e-2055-4411-bb55-5ca129c381a7"));
        assertTrue(UpdateMedicationHelper.isUsableProviderRevision(
                "bfe9e5a21ea3ac6a6c00abbb"));
    }
}
