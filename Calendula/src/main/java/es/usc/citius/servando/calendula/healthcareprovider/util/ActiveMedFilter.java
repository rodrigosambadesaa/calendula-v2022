package es.usc.citius.servando.calendula.healthcareprovider.util;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.model.persistence.ATCCode;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedCharacteristic;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedCodingSystemType;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVisualizationType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;

public class ActiveMedFilter {

    private static Map<ActiveMedCodingSystemType, Map<String, ActiveMedVisualizationType>> FilterTypes = new HashMap<>();
//    private static Map<ActiveMedVisualizationType, String> ExtraInfo = new HashMap<>();

    static {
        Map<String, ActiveMedVisualizationType> filterCodes = new HashMap<>();
        filterCodes.put("67866001", ActiveMedVisualizationType.INSULIN_TYPE);
        FilterTypes.put(ActiveMedCodingSystemType.SNOMED_CT, filterCodes);

        filterCodes = new HashMap<>();
        filterCodes.put("372862008", ActiveMedVisualizationType.ANTICOAGULANT_TYPE);
        FilterTypes.put(ActiveMedCodingSystemType.FHIR, filterCodes);

        filterCodes = new HashMap<>();
        filterCodes.put("B01AA", ActiveMedVisualizationType.ANTICOAGULANT_TYPE);
        filterCodes.put("B01AA07", ActiveMedVisualizationType.ANTICOAGULANT_TYPE);
        filterCodes.put("B01AA03", ActiveMedVisualizationType.ANTICOAGULANT_TYPE);
        FilterTypes.put(ActiveMedCodingSystemType.ATC, filterCodes);

    }

    public ActiveMedFilter() {
    }

    public static void filter(@NotNull ActiveMedVO medVO, @NotNull List<ActiveMedCharacteristic> characteristics) {
        if (medVO != null && characteristics.isEmpty()) {
            Prescription prescription = medVO.getBackingEntity().getPrescription();
            if (medVO.getActiveMedType() == ActiveMedType.NATIONAL_CODE && prescription != null) {
                ATCCode code = DB.drugDB().atcCodes().findOneBy(ATCCode.COLUMN_ATC_CODE_ID, prescription.getATCCode());
                if (code != null) {
                    characteristics.add(new ActiveMedCharacteristic(ActiveMedCodingSystemType.ATC, code.getATCCode()));
                }
            }
        }
        Map<String, ActiveMedVisualizationType> filterCodes;
        for (ActiveMedCharacteristic characteristic : characteristics
        ) {
            filterCodes = FilterTypes.get(characteristic.getCodingSystem());
            if (filterCodes != null && filterCodes.containsKey(characteristic.getValue())) {
                medVO.setVisualizationType(filterCodes.get(characteristic.getValue()));
            }
        }
    }
}
