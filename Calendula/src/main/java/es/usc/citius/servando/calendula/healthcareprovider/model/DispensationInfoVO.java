package es.usc.citius.servando.calendula.healthcareprovider.model;

import org.joda.time.Interval;
import org.joda.time.LocalDateTime;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;


public class DispensationInfoVO {

    private DispensationInfoEntity backingEntity;

    public DispensationInfoVO() {
        this.backingEntity = new DispensationInfoEntity();
    }

    public DispensationInfoVO(DispensationInfoEntity entity) {
        this.backingEntity = entity;
    }

    public DispensationInfoVO(boolean dispensed, Interval validityInterval, String dispensedCode, LocalDateTime dispensedDateTime) {
        this();
        backingEntity.setDispensed(dispensed);
        backingEntity.setDispenseInterval(validityInterval);
        backingEntity.setDispensedCode(dispensedCode);
        backingEntity.setDispensedDateTime(dispensedDateTime);
    }

    public boolean isDispensed() {
        return backingEntity.isDispensed();
    }

    public void setDispensed(boolean dispensed) {
        backingEntity.setDispensed(dispensed);
    }

    public Interval getValidityInterval() {
        return backingEntity.getDispenseInterval();
    }

    public void setValidityInterval(Interval validityInterval) {
        backingEntity.setDispenseInterval(validityInterval);
    }

    public String getDispensedCode() {
        return backingEntity.getDispensedCode();
    }

    public void setDispensedCode(String dispensedCode) {
        backingEntity.setDispensedCode(dispensedCode);
    }

    public LocalDateTime getDispensedDateTime() {
        return backingEntity.getDispensedDateTime();
    }

    public void setDispensedDateTime(LocalDateTime dispensedDateTime) {
        backingEntity.setDispensedDateTime(dispensedDateTime);
    }

    public DispensationInfoEntity toEntity(ActiveMedVO activeMed) {
        backingEntity.setActiveMed(activeMed.backingEntity);
        return backingEntity;
    }

}
