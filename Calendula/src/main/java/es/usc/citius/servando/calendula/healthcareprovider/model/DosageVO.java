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
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType;
import es.usc.citius.servando.calendula.healthcareprovider.util.DBUtil;

/**
 * Generic active med item.
 */
public class DosageVO {

    private static final String TAG = "DosageVO";
    protected final DosageEntity backingEntity;

    @NonNull
    public DosageType getType() {
        return backingEntity.getType();
    }

    public DosageVO(){
        backingEntity = new DosageEntity();
    }

    public DosageVO(ActiveMedVO activeMedVO, DosageEntity entity) {
        backingEntity = entity;
        backingEntity.setActiveMed(activeMedVO.backingEntity);
    }

    public void setType(DosageType type){
        backingEntity.setType(type);
    }

    public ActiveMedEntity getActiveMed() {
        return backingEntity.getActiveMed();
    }

    public void setActiveMed(ActiveMedEntity activeMed) {
        backingEntity.setActiveMed(activeMed);
    }

    public DosageEntity entity() {
        return backingEntity;
    }

    public DosageEntity toEntity(ActiveMedVO activeMedVO) {
        backingEntity.setActiveMed(activeMedVO.backingEntity);
        return backingEntity;
    }

    public List<DosageEntryVO> getEntries() {
       return DBUtil.toDosageEntryList(backingEntity.getEntries());
    }

    public void setEntries(List<DosageEntryVO> entries) {
        backingEntity.setEntries(DBUtil.toDosageEntryFc(entries));
    }

    @Override
    public String toString() {
        return backingEntity.toString();
    }

    public static class Builder {

        DosageVO dosageVO;
        List<DosageEntryVO> entries = new ArrayList<>();

        public Builder(){
            dosageVO = new DosageVO();
        }


        public Builder activeMed(ActiveMedVO med){
            dosageVO.setActiveMed(med.getBackingEntity());
            return this;
        }

        public Builder type(DosageType type){
            dosageVO.setType(type);
            return this;
        }

        public DosageEntryVO.Builder newEntry(){
            return new DosageEntryVO.Builder(this);
        }

        public Builder addEntry(DosageEntryVO entryVO){
            entries.add(entryVO);
            return this;
        }

        public DosageVO build(){
            dosageVO.setEntries(entries);
            return  dosageVO;
        }

    }

    public String toReadableString(Context ctx){
        StringBuilder out = new StringBuilder();
        for(DosageEntryVO de : getEntries()){
            String readableInfo = de.toReadableString(ctx);
            readableInfo = readableInfo.substring(0, 1).toUpperCase() +
                    readableInfo.substring(1).toLowerCase();
            out.append("● ").append(readableInfo).append("\n");
        }
        return out.toString();
    }

}
