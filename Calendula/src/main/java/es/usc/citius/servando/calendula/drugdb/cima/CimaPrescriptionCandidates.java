/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Non-persistent candidates for a future audited CIMA-to-Caléndula import.
 *
 * The same registration number can have several different presentations,
 * uniquely identified by their national code (CN). Never collapse them into
 * one database record or infer a prescription or dosage for a patient here.
 */
public final class CimaPrescriptionCandidates {

    private CimaPrescriptionCandidates() {
    }

    public static List<Candidate> from(CimaRestCatalog.MedicineSnapshot medicine) {
        if (medicine == null) {
            throw new IllegalArgumentException("Missing CIMA medicine metadata");
        }
        List<Candidate> candidates = new ArrayList<>();
        for (CimaRestCatalog.PresentationSnapshot presentation
                : medicine.getPresentations()) {
            candidates.add(new Candidate(medicine.getRegistrationNumber(),
                    presentation.getNationalCode(), presentation.getDisplayName(),
                    medicine.getDose()));
        }
        return Collections.unmodifiableList(candidates);
    }

    /**
     * Validate a batch of independently verified CIMA detail responses before
     * any future SQL transaction is considered. A national code must never
     * resolve to two disagreeing medicine registrations or package names.
     *
     * Identical repeats are idempotent. No existing catalog entries or
     * patient-linked medicine records are deleted, modified or persisted.
     */
    public static List<Candidate> compileBatch(
            List<CimaRestCatalog.MedicineSnapshot> medicines) {
        if (medicines == null) {
            throw new IllegalArgumentException("Missing CIMA medicine batch");
        }
        Map<String, Candidate> byNationalCode = new LinkedHashMap<>();
        for (CimaRestCatalog.MedicineSnapshot medicine : medicines) {
            for (Candidate candidate : from(medicine)) {
                Candidate previous = byNationalCode.get(candidate.getNationalCode());
                if (previous == null) {
                    byNationalCode.put(candidate.getNationalCode(), candidate);
                } else if (!previous.getRegistrationNumber()
                                .equals(candidate.getRegistrationNumber())
                        || !previous.getMarketedName().equals(candidate.getMarketedName())
                        || !Objects.equals(previous.getSourceDoseText(),
                                candidate.getSourceDoseText())) {
                    throw new IllegalArgumentException(
                            "Conflicting CIMA records for the same national code");
                }
            }
        }
        return Collections.unmodifiableList(
                new ArrayList<>(byNationalCode.values()));
    }

    /** Read-only candidate. Not an ORM entity and never automatically persisted. */
    public static final class Candidate {
        private final String registrationNumber;
        private final String nationalCode;
        private final String marketedName;
        private final String sourceDoseText;

        private Candidate(String registrationNumber, String nationalCode,
                          String marketedName, String sourceDoseText) {
            this.registrationNumber = registrationNumber;
            this.nationalCode = nationalCode;
            this.marketedName = marketedName;
            this.sourceDoseText = sourceDoseText;
        }

        public String getRegistrationNumber() {
            return registrationNumber;
        }

        public String getNationalCode() {
            return nationalCode;
        }

        public String getMarketedName() {
            return marketedName;
        }

        /** Source metadata only, NOT a medication dosing recommendation. */
        public String getSourceDoseText() {
            return sourceDoseText;
        }
    }
}
