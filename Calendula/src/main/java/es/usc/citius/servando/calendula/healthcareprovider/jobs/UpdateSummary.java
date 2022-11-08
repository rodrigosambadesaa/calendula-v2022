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

package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import java.util.ArrayList;
import java.util.List;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;


public class UpdateSummary {

    private List<ActiveMedEntity> created;
    private List<ActiveMedEntity> updated;
    private List<ActiveMedEntity> deleted;

    public int newCount() {
        return created != null ? created.size() : 0;
    }

    public int updatedCount() {
        return updated != null ? updated.size() : 0;
    }

    public int deletedCount() {
        return deleted != null ? deleted.size() : 0;
    }

    public boolean hasNew() {
        return created != null;
    }

    public boolean hasUpdated() {
        return updated != null;
    }

    public boolean hasDeleted() {
        return deleted != null;
    }

    public void addCreated(ActiveMedEntity e) {
        if (created == null)
            created = new ArrayList<>();
        created.add(e);
    }

    public void addUpdated(ActiveMedEntity e) {
        if (updated == null)
            updated = new ArrayList<>();
        updated.add(e);
    }

    public void addDeleted(ActiveMedEntity e) {
        if (deleted == null)
            deleted = new ArrayList<>();
        deleted.add(e);
    }

    public boolean hasChanges() {
        return created != null || updated != null || deleted != null;
    }

    public List<ActiveMedEntity> getCreated() {
        return created != null ? created : new ArrayList<ActiveMedEntity>();
    }

    public void setCreated(List<ActiveMedEntity> created) {
        this.created = created;
    }

    public List<ActiveMedEntity> getUpdated() {
        return updated != null ? updated : new ArrayList<ActiveMedEntity>();
    }

    public void setUpdated(List<ActiveMedEntity> updated) {
        this.updated = updated;
    }

    public List<ActiveMedEntity> getDeleted() {
        return deleted != null ? deleted : new ArrayList<ActiveMedEntity>();
    }

    public void setDeleted(List<ActiveMedEntity> deleted) {
        this.deleted = deleted;
    }

    public int changesCount() {
        return newCount() + updatedCount() + deletedCount();
    }
}
