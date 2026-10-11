/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import androidx.annotation.Nullable;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

/**
 * Only verified, correctly owned doses may be presented in the home agenda.
 * An inconsistent legacy row must not show another patient's medication
 * or a fabricated dose. This deliberately leaves the SQLite event intact
 * for diagnosis/recovery rather than deleting clinical history.
 */
public final class DailyAgendaEventValidator {

    private DailyAgendaEventValidator() {
    }

    @Nullable
    public static Double verifiedDose(EventInstance event, Schedule schedule) {
        if (event == null || schedule == null
                || event.getId() == null || event.getTime() == null
                || event.getType() != EventType.MEDICATION_INTAKE
                || event.getRef() == null || schedule.getId() == null
                || !event.getRef().equals(schedule.getId())) {
            return null;
        }
        Patient eventPatient = event.getPatient();
        Patient schedulePatient = schedule.getPatient();
        if (eventPatient == null || eventPatient.getId() == null
                || schedulePatient == null || schedulePatient.getId() == null
                || !eventPatient.getId().equals(schedulePatient.getId())) {
            return null;
        }
        Medicine medicine = schedule.getMedicine();
        if (medicine == null || medicine.getName() == null
                || medicine.getName().trim().isEmpty()
                || medicine.getPresentation() == null) {
            return null;
        }
        Patient medicinePatient = medicine.getPatient();
        // Legacy global catalog medicines may have no owner; specifically
        // assigned medicines must belong to the same patient as the event.
        if (medicinePatient != null && (medicinePatient.getId() == null
                || !eventPatient.getId().equals(medicinePatient.getId()))) {
            return null;
        }
        try {
            if (event.getParams() == null) {
                return null;
            }
            String raw = event.getParams().getString(EventInstance.PARAM_DOSE);
            if (raw == null) {
                return null;
            }
            double dose = Double.parseDouble(raw);
            return dose > 0d && !Double.isNaN(dose) && !Double.isInfinite(dose)
                    ? dose : null;
        } catch (RuntimeException malformed) {
            // A missing, wrong-typed or corrupted legacy Bundle cannot
            // authorize an exact amount of medicine in the UI.
            return null;
        }
    }
}
