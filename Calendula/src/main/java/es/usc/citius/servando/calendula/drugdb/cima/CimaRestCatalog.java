/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only foundation for the AEMPS CIMA REST API.
 *
 * CIMA returns structured JSON, not the legacy Calendula SQL download format.
 * Keep network access and database writes out of this class: schema reconciliation,
 * transactional imports and end-to-end validation must precede integration.
 */
public final class CimaRestCatalog {

    private static final String DETAILS_URL =
            "https://cima.aemps.es/cima/rest/medicamento?nregistro=";
    private static final int MAX_JSON_CHARS = 256 * 1024;

    private CimaRestCatalog() {
    }

    public static String detailUrl(String registrationNumber) {
        validateRegistration(registrationNumber);
        return DETAILS_URL + registrationNumber;
    }

    public static MedicineSnapshot parseMedicine(String json) {
        if (json == null || json.length() == 0 || json.length() > MAX_JSON_CHARS) {
            throw new IllegalArgumentException("Missing or oversized CIMA medicine response");
        }
        final JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Malformed CIMA medicine JSON", e);
        }
        if (root == null || !root.isJsonObject()) {
            throw new IllegalArgumentException("CIMA medicine response must be an object");
        }

        JsonObject medicine = root.getAsJsonObject();
        String registration = requireString(medicine, "nregistro");
        validateRegistration(registration);
        String name = requireString(medicine, "nombre").trim();
        if (name.isEmpty() || name.length() > 512) {
            throw new IllegalArgumentException("Invalid CIMA medicine name");
        }
        String dose = optionalString(medicine, "dosis");

        Set<String> codes = new LinkedHashSet<>();
        JsonElement presentations = medicine.get("presentaciones");
        if (presentations != null && !presentations.isJsonNull()) {
            if (!presentations.isJsonArray()) {
                throw new IllegalArgumentException("Invalid CIMA presentation collection");
            }
            JsonArray entries = presentations.getAsJsonArray();
            if (entries.size() > 1000) {
                throw new IllegalArgumentException("Too many CIMA presentations");
            }
            for (JsonElement entry : entries) {
                if (!entry.isJsonObject()) {
                    throw new IllegalArgumentException("Invalid CIMA presentation");
                }
                String code = requireString(entry.getAsJsonObject(), "cn");
                if (!code.matches("[0-9]{5,8}")) {
                    throw new IllegalArgumentException("Invalid CIMA national code");
                }
                codes.add(code);
            }
        }
        return new MedicineSnapshot(registration, name, dose,
                Collections.unmodifiableList(new ArrayList<>(codes)));
    }

    /**
     * Bind a retrieved detail response to the registration number requested from
     * CIMA. A valid but unrelated JSON record must never be associated with the
     * wrong medicine after a caching, proxy, or response-selection error.
     */
    public static MedicineSnapshot parseMedicine(String json, String expectedRegistration) {
        validateRegistration(expectedRegistration);
        MedicineSnapshot result = parseMedicine(json);
        if (!expectedRegistration.equals(result.getRegistrationNumber())) {
            throw new IllegalArgumentException(
                    "CIMA medicine response does not match requested registration");
        }
        return result;
    }

    private static void validateRegistration(String registration) {
        if (registration == null || !registration.matches("[0-9]{1,12}")) {
            throw new IllegalArgumentException("Invalid CIMA registration identifier");
        }
    }

    private static String requireString(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing or invalid CIMA field: " + field);
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IllegalArgumentException("Invalid CIMA field type: " + field);
        }
        return primitive.getAsString();
    }

    private static String optionalString(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid optional CIMA field type: " + field);
        }
        return element.getAsString();
    }

    /** Immutable, non-persisted medicine metadata; no dosage decisions are inferred. */
    public static final class MedicineSnapshot {

        private final String registrationNumber;
        private final String name;
        private final String dose;
        private final List<String> nationalCodes;

        private MedicineSnapshot(String registrationNumber, String name,
                                 String dose, List<String> nationalCodes) {
            this.registrationNumber = registrationNumber;
            this.name = name;
            this.dose = dose;
            this.nationalCodes = nationalCodes;
        }

        public String getRegistrationNumber() {
            return registrationNumber;
        }

        public String getName() {
            return name;
        }

        public String getDose() {
            return dose;
        }

        public List<String> getNationalCodes() {
            return nationalCodes;
        }
    }
}
