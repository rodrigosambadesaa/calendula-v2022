package es.usc.citius.servando.calendula.fragments;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;
import java.util.Objects;

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
                && ownsPatient(persisted, currentlyActive)
                && sameMedicationRevision(requested, displayed)
                && sameMedicationRevision(requested, persisted);
    }

    /**
     * An imported provider row can reuse its SQLite ID after a refresh or
     * restore even for the same patient. Do not attach dispensations from a
     * previous treatment to a different drug or a changed provider revision.
     * Neither raw codes nor patient identities are exposed to logs or URIs.
     */
    static boolean sameMedicationRevision(ActiveMedEntity first, ActiveMedEntity second) {
        return first != null && second != null
                && first.getType() != null && first.getType() == second.getType()
                && first.getCode() != null && !first.getCode().trim().isEmpty()
                && first.getCode().equals(second.getCode())
                && Objects.equals(first.getLastUpdated(), second.getLastUpdated());
    }

    private static boolean ownsPatient(ActiveMedEntity medicine, Patient expected) {
        return medicine.getPatient() != null
                && PatientOwnerGuard.sameStableIdentity(expected, medicine.getPatient());
    }
}
