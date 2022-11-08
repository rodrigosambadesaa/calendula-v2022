package es.usc.citius.servando.calendula.healthcareprovider.model;


import androidx.annotation.NonNull;

import org.joda.time.DateTime;

import java.util.List;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.util.DBUtil;

/**
 * Generic active med item.
 */
public abstract class ActiveMedVO {

    private static final String TAG = "ActiveMedVO";
    protected final ActiveMedEntity backingEntity;



    public ActiveMedVO() {
        backingEntity = new ActiveMedEntity();
        backingEntity.setType(getActiveMedType());
    }

    public ActiveMedVO(ActiveMedEntity backingEntity) {
        this.backingEntity = backingEntity;
    }

    public ActiveMedVO(Patient patient, String code, String defaultDisplay, List<DispensationInfoVO> dispensationInfo) {
        this();
        backingEntity.setPatient(patient);
        backingEntity.setCode(code);
        backingEntity.setDefaultDisplay(defaultDisplay);
        this.setDispensationInfo(dispensationInfo);
    }

    public static ActiveMedVO forEntity(@NonNull ActiveMedEntity entity) {
        switch (entity.getType()) {
            case DCPF:
                return new ActiveMedDCPFVO(entity);
            case NATIONAL_CODE:
                return new ActiveMedCNVO(entity);
        }
        return null;
    }

    public Patient getPatient() {
        return backingEntity.getPatient();
    }

    public void setPatient(Patient patient) {
        backingEntity.setPatient(patient);
    }

    public String getCode() {
        return backingEntity.getCode();
    }

    public void setCode(String code) {
        backingEntity.setCode(code);
    }

    public String getDefaultDisplay() {
        return backingEntity.getDefaultDisplay();
    }

    public void setDefaultDisplay(String defaultDisplay) {
        backingEntity.setDefaultDisplay(defaultDisplay);
    }

    public abstract String getDisplay();

    public List<DispensationInfoVO> getDispensationInfo() {
        return DBUtil.toDispensationVoList(backingEntity.getDispensationInfo());
    }

    public void setDispensationInfo(List<DispensationInfoVO> dispensationInfo) {
        backingEntity.setDispensationInfo(DBUtil.toDispensationEntityFC(dispensationInfo, this));
    }

    public DosageVO getDosage() {
        return new DosageVO(this, backingEntity.getDosage());
    }

    public void setDosage(DosageVO dosageVO) {
        backingEntity.setDosage(dosageVO.toEntity(this));
    }

    public DateTime getValidityStart() {
        return backingEntity.getValidityStart();
    }

    public void setValidityStart(DateTime start) {
        backingEntity.setValidityStart(start);
    }

    public DateTime getValidityEnd() {
        return backingEntity.getValidityEnd();
    }

    public void setValidityEnd(DateTime end) {
        backingEntity.setValidityEnd(end);
    }

    /**
     * @return the concrete type for this item
     */
    @NonNull
    public abstract ActiveMedType getActiveMedType();

    public ActiveMedEntity getBackingEntity() {
        return backingEntity;
    }

    public DateTime getLastUpdated() {
        return backingEntity.getLastUpdated();
    }

    public void setLastUpdated(DateTime lastUpdated) {
        backingEntity.setLastUpdated(lastUpdated);
    }

    public void setLastUpdated(DateTime lastUpdated, boolean seen) {
        backingEntity.setLastUpdated(lastUpdated, seen);
    }

    public boolean isUpdateSeen() {
        return backingEntity.isUpdateSeen();
    }

    public void setUpdateSeen(boolean updateSeen) {
        backingEntity.setUpdateSeen(updateSeen);
    }

    public ActiveMedEntity.ActiveMedState getState() {
        return backingEntity.getState();
    }

    public void setState(ActiveMedEntity.ActiveMedState state) {
        backingEntity.setState(state);
    }

    public boolean hasExtraInfo() {
        return backingEntity.getVisualizationType()!=null;
    }


    public ActiveMedVisualizationType getVisualizationType() {
        return backingEntity.getVisualizationType();
    }

    public void setVisualizationType(ActiveMedVisualizationType extraInfoType) {
        backingEntity.setVisualizationType(extraInfoType);
    }
}
