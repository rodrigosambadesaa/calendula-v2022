/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Disposable in-memory owner snapshots; no real provider session or records. */
@RunWith(AndroidJUnit4.class)
public class ProviderDispensationOwnerSmokeTest {
    private static Patient patient(long id, String code) {
        Patient result = new Patient();
        result.setId(id);
        result.setCode(code);
        return result;
    }

    private static ActiveMedEntity official(Patient owner) {
        ActiveMedEntity entity = new ActiveMedEntity();
        entity.setId(20261111L);
        entity.setPatient(owner);
        entity.setType(ActiveMedType.DCPF);
        entity.setCode("ci-official-synthetic-drug");
        return entity;
    }

    @Test
    public void oldAsyncDispensationCannotRenderAfterPatientIdReuse() {
        Patient original = patient(991001L, "ci-provider-original");
        Patient restored = patient(991001L, "ci-provider-replacement");
        ActiveMedEntity requested = official(original);
        ActiveMedEntity shown = official(patient(991001L, "ci-provider-original"));
        ActiveMedEntity stored = official(patient(991001L, "ci-provider-original"));

        assertTrue(ProviderDispensationRenderGuard.canApply(
                requested, shown, stored, original));
        assertFalse("The same row ID cannot replay previous patient's dispensations",
                ProviderDispensationRenderGuard.canApply(
                        requested, shown, stored, restored));
        assertFalse("Provider data changed while loading must be discarded",
                ProviderDispensationRenderGuard.canApply(
                        requested, shown, official(restored), original));
        assertFalse("Missing stable identity must fail closed",
                ProviderDispensationRenderGuard.canApply(
                        requested, shown, stored, patient(991001L, null)));
        ActiveMedEntity replacedDrug = official(patient(991001L, "ci-provider-original"));
        replacedDrug.setCode("ci-synthetic-restored-drug");
        assertFalse("Reused provider ID with changed drug code discards stale dispensations",
                ProviderDispensationRenderGuard.canApply(
                        requested, shown, replacedDrug, original));
        ActiveMedEntity changedType = official(patient(991001L, "ci-provider-original"));
        changedType.setType(ActiveMedType.NATIONAL_CODE);
        assertFalse("Changed official drug category cannot inherit stale async data",
                ProviderDispensationRenderGuard.canApply(
                        requested, shown, changedType, original));
        ActiveMedEntity inactiveReplacement =
                official(patient(991001L, "ci-provider-original"));
        inactiveReplacement.setState(ActiveMedEntity.ActiveMedState.INACTIVE);
        assertFalse("Inactive restored official treatment cannot inherit a pending result",
                ProviderDispensationRenderGuard.canApply(
                        requested, shown, inactiveReplacement, original));
    }
}
