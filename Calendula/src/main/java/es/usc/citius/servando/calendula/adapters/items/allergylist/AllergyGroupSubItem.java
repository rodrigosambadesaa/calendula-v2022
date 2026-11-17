/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
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

package es.usc.citius.servando.calendula.adapters.items.allergylist;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;


public class AllergyGroupSubItem extends AbstractExpandableItem<AllergyGroupItem, AllergyGroupSubItem.ViewHolder, AllergyGroupSubItem> implements Comparable<AllergyGroupSubItem> {

    private PatientAllergen allergen;
    private String title;
    private String subtitle;
    private AllergyGroupItem parent;

    public AllergyGroupSubItem(PatientAllergen vo, Context context) {
        this.allergen = vo;
        // Old imports can omit an allergen name or type. Keep the row
        // visible for clinical review rather than crashing the allergy list.
        this.title = vo.getName() == null || vo.getName().trim().isEmpty()
                ? context.getString(R.string.allergy_entry_unverified)
                : vo.getName();
        if (vo.getType() == null) {
            subtitle = context.getString(R.string.allergy_entry_unverified);
        } else switch (vo.getType()) {
            case ACTIVE_INGREDIENT:
                subtitle = context.getString(R.string.active_ingredient);
                break;
            case EXCIPIENT:
                subtitle = context.getString(R.string.excipient);
                break;
            default:
                subtitle = context.getString(R.string.allergy_entry_unverified);
        }
    }

    @Override
    public void bindView(ViewHolder holder, List<Object> payloads) {
        super.bindView(holder, payloads);
        holder.title.setText(title);
        holder.subtitle.setText(subtitle);
    }

    @Override
    public void unbindView(ViewHolder holder) {
        super.unbindView(holder);
        holder.title.setText(null);
        holder.subtitle.setText(null);
    }

    @NonNull
    @Override
    public ViewHolder getViewHolder(View v) {
        // Returning null makes FastAdapter unable to create grouped rows.
        return new ViewHolder(v);
    }

    @Override
    public int compareTo(@NonNull AllergyGroupSubItem o) {
        return this.title.compareTo(o.title);
    }

    public PatientAllergen getAllergen() {
        return allergen;
    }

    @Override
    public AllergyGroupItem getParent() {
        return parent;
    }

    public void setParent(AllergyGroupItem parent) {
        this.parent = parent;
    }

    @Override
    public boolean isSelectable() {
        return false;
    }

    @Override
    public int getType() {
        return R.id.fastadapter_allergy_group_sub_item;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.allergen_search_group_sub_list_item;
    }

    @Override
    public void attachToWindow(ViewHolder holder) {

    }

    public String getTitle() {
        return title;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.text1)
        TextView title;
        @BindView(R.id.text2)
        TextView subtitle;

        public ViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }

}
