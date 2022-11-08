package es.usc.citius.servando.calendula.healthcareprovider.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import es.usc.citius.servando.calendula.util.LogUtil;

public enum ActiveMedCodingSystemType {

    /**
     * Active med is a DCPF code, not a specific med.
     */
    DCPF("2.16.724.4.21.5.15.3"),
    /**
     * Active med is a national code (specific med).
     */
    NATIONAL_CODE("2.16.724.4.21.5.15.4"),
    /**
     * Active med has a characteristic with ATC code.
     */
    ATC("http://www.whocc.no/atc"),
    /**
     * Active med has a characteristic with SNOMED-CT code.
     */
    SNOMED_CT("http://snomed.info/sct"),
    /**
     * Active med has a characteristic with FHIR medication code.
     */
    FHIR("http://hl7.org/fhir/ValueSet/medication-codes");

    private static final String TAG = "ActiveMedCodingSystemType";
    private static final Map<String, ActiveMedCodingSystemType> codingToEnum;

    static {
        HashMap<String, ActiveMedCodingSystemType> theMap = new HashMap<>();
        for (ActiveMedCodingSystemType areaCode : values()) {
            theMap.put(areaCode.getCoding(), areaCode);
        }
        codingToEnum = Collections.unmodifiableMap(theMap);
    }

    private final String coding;

    ActiveMedCodingSystemType(String coding) {
        this.coding = coding;
    }

    public static ActiveMedCodingSystemType forCoding(final String coding) {
        final ActiveMedCodingSystemType type = codingToEnum.get(coding);
        if (type == null) {
            LogUtil.e(TAG, "forCoding: unknown coding " + coding);
        }
        return type;
    }

    public String getCoding() {
        return coding;
    }
}