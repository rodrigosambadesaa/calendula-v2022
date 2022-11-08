package es.usc.citius.servando.calendula.healthcareprovider.model;

import androidx.annotation.NonNull;

import java.util.List;

import es.usc.citius.servando.calendula.drugdb.model.persistence.HomogeneousGroup;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;


public class ActiveMedDCPFVO extends ActiveMedVO {

    private static final String TAG = "ActiveMedDCPFVO";

    public ActiveMedDCPFVO() {
        super();
    }

    public ActiveMedDCPFVO(ActiveMedEntity backingEntity) {
        super(backingEntity);
    }

    public ActiveMedDCPFVO(Patient patient, String code, String defaultDisplay, List<DispensationInfoVO> dispensationInfo, HomogeneousGroup homogeneousGroup) {
        super(patient, code, defaultDisplay, dispensationInfo);
        backingEntity.setHomogeneousGroup(homogeneousGroup);
    }

    @Override
    public String getDisplay() {
        if (backingEntity.getHomogeneousGroup() != null) {
            return backingEntity.getHomogeneousGroup().getName();
        } else {
            return getDefaultDisplay();
        }
    }

    @NonNull
    @Override
    public ActiveMedType getActiveMedType() {
        return ActiveMedType.DCPF;
    }

    public HomogeneousGroup getHomogeneousGroup() {
        return backingEntity.getHomogeneousGroup();
    }

    public void setHomogeneousGroup(HomogeneousGroup homogeneousGroup) {
        backingEntity.setHomogeneousGroup(homogeneousGroup);
    }

}
