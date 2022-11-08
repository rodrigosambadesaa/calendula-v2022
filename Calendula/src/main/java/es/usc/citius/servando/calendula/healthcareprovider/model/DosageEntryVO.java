/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.healthcareprovider.model;


import android.content.Context;

import org.hl7.fhir.dstu3.model.Timing;
import org.joda.time.LocalTime;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType;
import es.usc.citius.servando.calendula.healthcareprovider.util.StringUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.Strings;

/**
 * Generic active med item.
 */
public class DosageEntryVO {

    private static final String TAG = "DosageEntryVO";
    protected final DosageEntryEntity backingEntity;

    private DosageEntryVO(DosageVO dosageVO) {
        backingEntity = new DosageEntryEntity(dosageVO.backingEntity);
    }

    public DosageEntryVO(DosageEntryEntity backingEntity) {
        this.backingEntity = backingEntity;
    }

    public String getText() {
        return backingEntity.getText();
    }

    public void setText(String text) {
        backingEntity.setText(text);
    }

    public String getPatientInstruction() {
        return backingEntity.getPatientInstruction();
    }

    public void setPatientInstruction(String patientInstruction) {
        backingEntity.setPatientInstruction(patientInstruction);
    }

    public RepeatType getRepeatType() {
        return backingEntity.getRepeatType();
    }

    public void setRepeatType(RepeatType rType) {
        backingEntity.setRepeatType(rType);
    }

    public Double getRepeatValue() {
        return backingEntity.getRepeatValue();
    }

    public void setRepeatValue(Double repeatValue) {
        backingEntity.setRepeatValue(repeatValue);
    }

    public Timing.UnitsOfTime getRepeatUnits() {
        return backingEntity.getRepeatUnits();
    }

    public void setRepeatUnits(Timing.UnitsOfTime repeatUnits) {
        backingEntity.setRepeatUnits(repeatUnits);
    }

    public String getQuantityUnits() {
        return backingEntity.getQuantityUnits();
    }

    public void setQuantityUnits(String quantityUnits) {
        backingEntity.setQuantityUnits(quantityUnits);
    }

    public Double getQuantityValue() {
        return backingEntity.getQuantityValue();
    }

    public void setQuantityValue(Double quantityValue) {
        backingEntity.setQuantityValue(quantityValue);
    }

    public LocalTime getAt() {
        return backingEntity.getAt();
    }

    public void setAt(LocalTime at) {
        backingEntity.setAt(at);
    }

    public DosageEntryEntity toEntity(DosageVO dosageVO) {
        backingEntity.setDosage(dosageVO.backingEntity);
        return backingEntity;
    }

    public DosageEntryEntity entity() {
        return backingEntity;
    }

    @Override
    public String toString() {
        return "DosageEntryVO{" +
                "backingEntity=" + backingEntity.toString() +
                '}';
    }

    public String toReadableString(Context ctx) {

        final RepeatType repeatType = getRepeatType();
        if (repeatType.isMeal()) {
            return ctx.getString(R.string.dosage_string_meal_template, Strings.prettyDouble(getQuantityValue()), getQuantityUnits(), repeatType.getMealContextString(ctx));
        } else {
            switch (repeatType) {
                case PERIOD:
                    final String repeatUnitsDispl = StringUtils.timeUnitDisplayName(ctx, getRepeatUnits());
                    return ctx.getString(R.string.dosage_string_period, Strings.prettyDouble(getQuantityValue()), getQuantityUnits(), Strings.prettyDouble(getRepeatValue()), repeatUnitsDispl);
                case DURATION:
                    final String repeatUnitsDisp = StringUtils.timeUnitDisplayName(ctx, getRepeatUnits());
                    return ctx.getString(R.string.dosage_string_duration, Strings.prettyDouble(getRepeatValue()), repeatUnitsDisp);
                case TIME_OF_DAY:
                    return ctx.getString(R.string.dosage_string_time_of_day, Strings.prettyDouble(getQuantityValue()), getQuantityUnits(), getAt().toString("H:mm"));
                default:
                    LogUtil.w(TAG, "toReadableString: Unknown repeatType " + repeatType);
                    return ctx.getString(R.string.dosage_string_not_available);
            }
        }
    }

    public static class Builder {

        DosageEntryVO dosageEntryVO;
        DosageVO.Builder dosageVOBuilder;

        public Builder(DosageVO.Builder dosageBuilder) {
            this.dosageEntryVO = new DosageEntryVO(dosageBuilder.dosageVO);
            this.dosageVOBuilder = dosageBuilder;
        }

        public Builder quantity(Double value, String units) {
            this.dosageEntryVO.setQuantityValue(value);
            this.dosageEntryVO.setQuantityUnits(units);
            return this;
        }

        public Builder repeatDuration(Double duration, Timing.UnitsOfTime durationUnits) {
            dosageEntryVO.setRepeatType(RepeatType.DURATION);
            dosageEntryVO.setRepeatValue(duration);
            dosageEntryVO.setRepeatUnits(durationUnits);
            return this;
        }

        public Builder repeatPeriod(Double period, Timing.UnitsOfTime periodUnits) {
            dosageEntryVO.setRepeatType(RepeatType.PERIOD);
            dosageEntryVO.setRepeatValue(period);
            dosageEntryVO.setRepeatUnits(periodUnits);
            return this;
        }

        public Builder repeatType(RepeatType rType) {
            dosageEntryVO.setRepeatType(rType);
            return this;
        }

        public Builder repeatAt(LocalTime when) {
            dosageEntryVO.setRepeatType(RepeatType.TIME_OF_DAY);
            dosageEntryVO.setAt(when);
            return this;
        }

        public Builder text(String text) {
            dosageEntryVO.setText(text);
            return this;
        }

        public Builder instructions(String instruction) {
            dosageEntryVO.setPatientInstruction(instruction);
            return this;
        }


        public DosageEntryVO build() {

            if (dosageEntryVO.getRepeatType() == null) {
                throw new RuntimeException("RepeatType must be specified");
            }

            // TODO: check consistency
            return dosageEntryVO;
        }

        public DosageVO.Builder done() {
            dosageVOBuilder.addEntry(build());
            return dosageVOBuilder;
        }

    }


}
