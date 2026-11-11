package es.usc.citius.servando.calendula.fragments;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;

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
                || !PatientOwnerGuard.hasStableIdentity(currentlyActive)) {
            return false;
        }
        final Long officialId = requested.getId();
        if (officialId == null || officialId <= 0L
                || !officialId.equals(displayed.getId())
                || !officialId.equals(persisted.getId())) {
            return false;
        }
        // An asynchronous request can survive a restore that recycles the
        // numeric patient row ID. Require the same nonblank stable identity
        // for the request, currently displayed medication and live row.
        return ownsPatient(requested, currentlyActive)
                && ownsPatient(displayed, currentlyActive)
                && ownsPatient(persisted, currentlyActive);
    }

    private static boolean ownsPatient(ActiveMedEntity medicine, Patient expected) {
        return medicine.getPatient() != null
                && PatientOwnerGuard.sameStableIdentity(expected, medicine.getPatient());
    }
}
