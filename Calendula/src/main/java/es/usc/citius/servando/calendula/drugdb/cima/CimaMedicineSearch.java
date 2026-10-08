/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Official CIMA paginated medicine-name search. Search results are immutable,
 * read-only metadata, NOT prescriptions and never directly persisted.
 */
public final class CimaMedicineSearch {
    private static final String SEARCH_URL =
            "https://cima.aemps.es/cima/rest/medicamentos?nombre=";
    private static final int MAX_RESPONSE_CHARS = 256 * 1024;
    private static final int MAX_PAGE = 1000;
    private static final int MAX_RESULTS_PER_PAGE = 500;
    private static final int MAX_TOTAL_RESULTS = 1000000;

    private CimaMedicineSearch() {
    }

    public static String url(String medicineName, int page) {
        if (medicineName == null) {
            throw new IllegalArgumentException("A medicine name is required");
        }
        String query = medicineName.trim();
        if (query.length() < 2 || query.length() > 80) {
            throw new IllegalArgumentException("Medicine search must be 2 to 80 characters");
        }
        for (int i = 0; i < query.length(); i++) {
            if (Character.isISOControl(query.charAt(i))) {
                throw new IllegalArgumentException("Control characters in medicine search");
            }
        }
        if (page < 1 || page > MAX_PAGE) {
            throw new IllegalArgumentException("CIMA page is outside allowed range");
        }
        try {
            // Explicit encoding prevents injection of arbitrary query parameters.
            return SEARCH_URL + URLEncoder.encode(query, "UTF-8") + "&pagina=" + page;
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 support is required", impossible);
        }
    }

    public static Page parsePage(String json, int expectedPage) {
        if (expectedPage < 1 || expectedPage > MAX_PAGE
                || json == null || json.isEmpty()
                || json.length() > MAX_RESPONSE_CHARS) {
            throw new IllegalArgumentException("Invalid CIMA search input");
        }
        final JsonElement data;
        try {
            data = JsonParser.parseString(json);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Invalid CIMA search JSON", exception);
        }
        if (data == null || !data.isJsonObject()) {
            throw new IllegalArgumentException("CIMA search response must be an object");
        }
        JsonObject root = data.getAsJsonObject();
        int page = intField(root, "pagina", 1, MAX_PAGE);
        int total = intField(root, "totalFilas", 0, MAX_TOTAL_RESULTS);
        if (page != expectedPage) {
            throw new IllegalArgumentException("CIMA search returned the wrong page");
        }

        JsonElement rows = root.get("resultados");
        if (rows == null || !rows.isJsonArray()) {
            throw new IllegalArgumentException("Missing CIMA search results");
        }
        JsonArray items = rows.getAsJsonArray();
        if (items.size() > MAX_RESULTS_PER_PAGE || items.size() > total) {
            throw new IllegalArgumentException("Invalid CIMA page result count");
        }
        Map<String, Result> results = new LinkedHashMap<>();
        for (JsonElement item : items) {
            if (!item.isJsonObject()) {
                throw new IllegalArgumentException("Invalid CIMA medicine summary");
            }
            JsonObject row = item.getAsJsonObject();
            String registration = requiredText(row, "nregistro", 12);
            if (!registration.matches("[0-9]{1,12}")) {
                throw new IllegalArgumentException("Invalid CIMA medicine registration");
            }
            String name = requiredText(row, "nombre", 512);
            Boolean marketed = optionalBoolean(row, "comerc");
            Boolean prescriptionRequired = optionalBoolean(row, "receta");
            Result earlier = results.get(registration);
            if (earlier == null) {
                results.put(registration,
                        new Result(registration, name, marketed, prescriptionRequired));
            } else if (!earlier.name.equals(name)
                    || !java.util.Objects.equals(earlier.marketed, marketed)
                    || !java.util.Objects.equals(earlier.prescriptionRequired,
                            prescriptionRequired)) {
                throw new IllegalArgumentException(
                        "Conflicting CIMA summaries for one medicine registration");
            }
        }
        return new Page(page, total,
                Collections.unmodifiableList(new ArrayList<>(results.values())));
    }

    private static int intField(JsonObject object, String field, int min, int max) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing numeric CIMA field: " + field);
        }
        try {
            long number = Long.parseLong(value.getAsString());
            if (number < min || number > max) {
                throw new IllegalArgumentException("Out-of-range CIMA field: " + field);
            }
            return (int) number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer CIMA field: " + field, e);
        }
    }

    private static String requiredText(JsonObject object, String field, int maxLength) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing CIMA medicine field: " + field);
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IllegalArgumentException("Invalid CIMA medicine field: " + field);
        }
        String text = primitive.getAsString().trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new IllegalArgumentException("Invalid length for CIMA field: " + field);
        }
        return text;
    }

    private static Boolean optionalBoolean(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Invalid CIMA boolean: " + field);
        }
        return element.getAsBoolean();
    }

    public static final class Page {
        private final int pageNumber;
        private final int totalResults;
        private final List<Result> results;

        private Page(int pageNumber, int totalResults, List<Result> results) {
            this.pageNumber = pageNumber;
            this.totalResults = totalResults;
            this.results = results;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public int getTotalResults() {
            return totalResults;
        }

        public List<Result> getResults() {
            return results;
        }
    }

    /** Non-clinical search metadata; null flags mean the field was not supplied. */
    public static final class Result {
        private final String registrationNumber;
        private final String name;
        private final Boolean marketed;
        private final Boolean prescriptionRequired;

        private Result(String registrationNumber, String name, Boolean marketed,
                       Boolean prescriptionRequired) {
            this.registrationNumber = registrationNumber;
            this.name = name;
            this.marketed = marketed;
            this.prescriptionRequired = prescriptionRequired;
        }

        public String getRegistrationNumber() {
            return registrationNumber;
        }

        public String getName() {
            return name;
        }

        public Boolean isMarketed() {
            return marketed;
        }

        public Boolean isPrescriptionRequired() {
            return prescriptionRequired;
        }
    }
}
