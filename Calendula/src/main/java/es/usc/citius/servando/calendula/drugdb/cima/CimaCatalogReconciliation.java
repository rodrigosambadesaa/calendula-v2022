/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;

/**
 * Pure, read-only reconciliation preview for future AEMPS catalog migrations.
 *
 * NEVER deletes or updates an existing Prescription or patient-linked medicine.
 * String equality does NOT establish medical equivalence; differences are
 * flagged for manual review rather than silently reassigning identifiers.
 */
public final class CimaCatalogReconciliation {

    private CimaCatalogReconciliation() {
    }

    public enum Status {
        /** New national code without a matching legacy catalog row. */
        NEW_PRESENTATION,
        /** Identical source identifiers and metadata; no changes required. */
        UNCHANGED_METADATA,
        /** An existing national code differs and requires clinical/data review. */
        REQUIRES_REVIEW,
        /** Legacy catalog entry not present in this CIMA batch: retain it. */
        HISTORICAL_ONLY
    }

    public static Plan preview(
            List<CimaPrescriptionCandidates.Candidate> official,
            List<Prescription> historical) {
        if (official == null || historical == null) {
            throw new IllegalArgumentException("Both medicine catalogs are required");
        }

        Map<String, Prescription> existingByCn = new LinkedHashMap<>();
        List<Entry> decisions = new ArrayList<>();
        // Existing rows without usable CNs must never be discarded or assigned
        // invented codes. Count them separately as preserved legacy records.
        int noCodeLegacyRows = 0;
        for (Prescription old : historical) {
            if (old == null) {
                throw new IllegalArgumentException("Null legacy prescription");
            }
            String cn = old.getCode();
            if (cn == null || cn.trim().isEmpty()) {
                noCodeLegacyRows++;
                continue;
            }
            if (existingByCn.put(cn, old) != null) {
                throw new IllegalArgumentException(
                        "Duplicate national code in legacy catalog; manual cleanup required");
            }
        }

        Map<String, CimaPrescriptionCandidates.Candidate> verified = new LinkedHashMap<>();
        for (CimaPrescriptionCandidates.Candidate source : official) {
            if (source == null || source.getNationalCode() == null
                    || !source.getNationalCode().matches("[0-9]{5,8}")
                    || source.getRegistrationNumber() == null
                    || source.getMarketedName() == null) {
                throw new IllegalArgumentException("Invalid official presentation candidate");
            }
            // Android 6 compatibility: avoid the Map.putIfAbsent default method.
            CimaPrescriptionCandidates.Candidate previous =
                    verified.get(source.getNationalCode());
            if (previous == null) {
                verified.put(source.getNationalCode(), source);
            } else if (!previous.getRegistrationNumber().equals(
                            source.getRegistrationNumber())
                    || !previous.getMarketedName().equals(source.getMarketedName())
                    || !Objects.equals(previous.getSourceDoseText(),
                            source.getSourceDoseText())) {
                throw new IllegalArgumentException(
                        "Conflicting official medicine data for a national code");
            }
        }

        for (CimaPrescriptionCandidates.Candidate source : verified.values()) {
            Prescription old = existingByCn.remove(source.getNationalCode());
            Status status;
            if (old == null) {
                status = Status.NEW_PRESENTATION;
            } else if (Objects.equals(source.getRegistrationNumber(), old.getPID())
                    && Objects.equals(source.getMarketedName(), old.getName())
                    && Objects.equals(source.getSourceDoseText(), old.getDose())) {
                status = Status.UNCHANGED_METADATA;
            } else {
                status = Status.REQUIRES_REVIEW;
            }
            decisions.add(new Entry(source.getNationalCode(), status));
        }

        // Do not infer that a catalog item missing from a partial CIMA batch
        // is withdrawn, obsolete, or safe to delete.
        for (String historicalCode : existingByCn.keySet()) {
            decisions.add(new Entry(historicalCode, Status.HISTORICAL_ONLY));
        }

        return new Plan(
                Collections.unmodifiableList(decisions),
                noCodeLegacyRows);
    }

    /** Immutable, non-persistent decision record; no clinical advice. */
    public static final class Entry {
        private final String nationalCode;
        private final Status status;

        private Entry(String nationalCode, Status status) {
            this.nationalCode = nationalCode;
            this.status = status;
        }

        public String getNationalCode() {
            return nationalCode;
        }

        public Status getStatus() {
            return status;
        }
    }

    /** Immutable preview; contains no SQL commands and performs no writes. */
    public static final class Plan {
        private final List<Entry> entries;
        private final int preservedLegacyRowsWithoutCode;

        private Plan(List<Entry> entries, int preservedLegacyRowsWithoutCode) {
            this.entries = entries;
            this.preservedLegacyRowsWithoutCode = preservedLegacyRowsWithoutCode;
        }

        public List<Entry> getEntries() {
            return entries;
        }

        public int getPreservedLegacyRowsWithoutCode() {
            return preservedLegacyRowsWithoutCode;
        }
    }
}
