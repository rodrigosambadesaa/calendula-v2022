package es.usc.citius.servando.calendula.fragments;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.persistence.Patient;

/**
 * A completed background dispensation fetch may update the visible page only
 * while the same persisted official medication and patient remain selected.
 */
public final class ProviderDispensationRenderGuard {
    private ProviderDispensationRenderGuard() { }

    public static boolean canApply(ActiveMedEntity requested,
                                   ActiveMedEntity displayed,
                                   ActiveMedEntity persisted,
                                   Patient currentlyActive) {
        if (requested == null || displayed == null || persisted == null
                || currentlyActive == null || currentlyActive.getId() == null
                || currentlyActive.getId() <= 0L) {
            return false;
        }
        final Long patientId = currentlyActive.getId();
        final Long officialId = requested.getId();
        if (officialId == null || officialId <= 0L
                || !officialId.equals(displayed.getId())
                || !officialId.equals(persisted.getId())) {
            return false;
        }
        return ownsPatient(requested, patientId)
                && ownsPatient(displayed, patientId)
                && ownsPatient(persisted, patientId);
    }

    private static boolean ownsPatient(ActiveMedEntity medicine, Long expectedId) {
        return medicine.getPatient() != null
                && medicine.getPatient().getId() != null
                && expectedId.equals(medicine.getPatient().getId());
    }
}
