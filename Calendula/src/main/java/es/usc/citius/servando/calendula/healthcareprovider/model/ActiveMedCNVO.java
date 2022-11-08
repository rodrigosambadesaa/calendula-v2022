package es.usc.citius.servando.calendula.healthcareprovider.model;

import androidx.annotation.NonNull;

import java.util.List;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;


public class ActiveMedCNVO extends ActiveMedVO {

    private static final String TAG = "ActiveMedCNVO";


    public ActiveMedCNVO() {
        super();
    }

    public ActiveMedCNVO(ActiveMedEntity backingEntity) {
        super(backingEntity);
    }

    public ActiveMedCNVO(Patient patient, String code, String defaultDisplay, List<DispensationInfoVO> dispensationInfo, Prescription prescription) {
        super(patient, code, defaultDisplay, dispensationInfo);
        backingEntity.setPrescription(prescription);
    }

    @Override
    public String getDisplay() {
        if (backingEntity.getPrescription() != null) {
            return getPrescription().getName();
        } else {
            return getDefaultDisplay();
        }
    }

    @NonNull
    @Override
    public ActiveMedType getActiveMedType() {
        return ActiveMedType.NATIONAL_CODE;
    }

    public Prescription getPrescription() {
        return backingEntity.getPrescription();
    }

    public void setPrescription(Prescription prescription) {
        backingEntity.setPrescription(prescription);
    }


}
