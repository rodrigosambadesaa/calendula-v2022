/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import androidx.annotation.Nullable;

import java.util.List;

import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageEntryVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;

/** Fail closed if the external treatment row is missing or belongs to another patient. */
public final class DailyAgendaOfficialTreatmentValidator {
    private DailyAgendaOfficialTreatmentValidator() {
    }

    /**
     * Imported prescriptions are clinical data. SQLite numeric patient IDs
     * can be reused after restore or erasure: an ID-only match does not
     * authorize using a provider's dose or instructions for another owner.
     * Unidentified legacy rows must first be reconciled with a stable code.
     */
    static boolean sameStableOwner(Patient expected, Patient actual) {
        if (expected == null || actual == null
                || expected.getId() == null || expected.getId() <= 0L
                || !expected.getId().equals(actual.getId())) {
            return false;
        }
        String expectedCode = expected.getCode();
        String actualCode = actual.getCode();
        return expectedCode != null && !expectedCode.trim().isEmpty()
                && actualCode != null && !actualCode.trim().isEmpty()
                && expectedCode.equals(actualCode);
    }

    public static boolean verifiedLink(Schedule schedule, ActiveMedEntity external) {
        if (schedule == null || !schedule.isBoundToActiveMed()
                || schedule.getActiveMedId() == null || schedule.getActiveMedId() <= 0L
                || external == null || external.getId() == null
                || !schedule.getActiveMedId().equals(external.getId())
                || external.getType() == null || external.getDosage() == null) {
            return false;
        }
        Patient scheduleOwner = schedule.getPatient();
        Patient externalOwner = external.getPatient();
        return sameStableOwner(scheduleOwner, externalOwner);
    }

    /**
     * Schedule editor previews must be bound to the patient currently active
     * in the UI, in addition to checking the imported provider row itself.
     * Missing/stale provider data may not be displayed across profiles.
     */
    public static boolean verifiedLinkForActivePatient(
            Schedule schedule, ActiveMedEntity external, Patient activePatient) {
        return schedule != null
                && sameStableOwner(activePatient, schedule.getPatient())
                && verifiedLink(schedule, external);
    }

    @Nullable
    public static String firstInstruction(Schedule schedule, ActiveMedEntity external) {
        if (!verifiedLink(schedule, external)) {
            return null;
        }
        try {
            ActiveMedVO medication = ActiveMedVO.forEntity(external);
            if (medication == null || medication.getDosage() == null) {
                return null;
            }
            List<DosageEntryVO> entries = medication.getDosage().getEntries();
            if (entries == null || entries.isEmpty() || entries.get(0) == null) {
                return null;
            }
            String instruction = entries.get(0).getPatientInstruction();
            return instruction == null || instruction.trim().isEmpty()
                    ? null : instruction;
        } catch (RuntimeException malformed) {
            // Legacy treatment metadata is optional display content. Do not
            // let a malformed provider row crash the medication agenda.
            return null;
        }
    }
}
