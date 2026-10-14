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

package es.usc.citius.servando.calendula.adapters.items;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.view.IconicsImageView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.modules.ModuleManager;
import es.usc.citius.servando.calendula.modules.modules.StockModule;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;


public class MedicineItem extends AbstractItem<MedicineItem, MedicineItem.MedicineViewHolder> {

    private Medicine medicine;

    public MedicineItem(Medicine m) {
        this.medicine = m;
    }


    public Medicine getMedicine() {
        return medicine;
    }

    @Override
    public int getType() {
        return R.id.fastadapter_medicine_item;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.medicines_list_item;
    }

    @Override
    public boolean isSelectable() {
        return false;
    }

    @Override
    public void bindView(MedicineViewHolder holder, List<Object> payloads) {
        super.bindView(holder, payloads);
        Context ctx = holder.itemView.getContext();

        // Legacy rows may have no presentation. A missing value must not
        // prevent binding the rest of a patient's medicine list.
        Presentation presentation = medicine.getPresentation() == null
                ? Presentation.UNKNOWN : medicine.getPresentation();
        holder.name.setText(medicine.getName());
        holder.icon.setImageDrawable(new IconicsDrawable(ctx)
                .icon(presentation.icon())
                .colorRes(R.color.agenda_item_title)
                .paddingDp(8)
                .sizeDp(40));
        holder.icon.setContentDescription(presentation.getName(ctx.getResources()));

        if (ModuleManager.isEnabled(StockModule.ID)) {
            holder.stockInfo.setVisibility(View.VISIBLE);

            final Float remaining = medicine.getStock();
            if (remaining != null && !Float.isNaN(remaining)
                    && !Float.isInfinite(remaining) && remaining >= 0f) {
                // Preserve fractional quantities and agree with the alert
                // card's %s formatting in all supported locales.
                final String amount = remaining == remaining.intValue()
                        ? Integer.toString(remaining.intValue())
                        : Float.toString(remaining);
                holder.stockInfo.setText(ctx.getString(R.string.stock_remaining_msg,
                        amount, presentation.units(ctx.getResources(), remaining)));
            } else {
                holder.stockInfo.setText(R.string.no_stock_info_msg);
            }
        }

        // Reset *every* recycled card. A previous medicine's alert icon must
        // not survive on a newly bound medicine with no clinical warnings.
        holder.alertIcon.setVisibility(View.GONE);
        holder.alertIcon.setImageDrawable(null);
        holder.alertIcon.setContentDescription(null);
        try {
            // A medicine ID alone is not authorization to read clinical alerts.
            // The scoped DAO checks both the persisted medicine owner and
            // every returned alert's owner before yielding a result.
            List<PatientAlert> alerts = DB.alerts().findByMedicineSortByLevel(medicine);
            if (!alerts.isEmpty()) {
                int level = PatientAlert.Level.LOW;
                for (PatientAlert a : alerts) {
                    if (a.getLevel() > level) {
                        level = a.getLevel();
                    }
                }
                holder.alertIcon.setImageDrawable(IconUtils.alertLevelIcon(level, ctx));
                holder.alertIcon.setContentDescription(ctx.getString(R.string.label_alerts_short));
                holder.alertIcon.setVisibility(View.VISIBLE);
            }
        } catch (RuntimeException error) {
            // A corrupt/misassigned alert is NOT "no alerts". Show a neutral
            // read-failure warning without assigning an invented severity or
            // revealing another patient's alert information.
            LogUtil.e("MedicineItem", "Clinical alerts could not be verified", error);
            holder.alertIcon.setImageDrawable(new IconicsDrawable(ctx)
                    .icon(CommunityMaterial.Icon.cmd_alert)
                    .colorRes(R.color.android_orange_dark).sizeDp(24));
            holder.alertIcon.setContentDescription(
                    ctx.getString(R.string.medicine_alerts_unavailable));
            holder.alertIcon.setVisibility(View.VISIBLE);
        }

        if (medicine.isFromActiveMed()) {
            holder.providerBgIcon.setVisibility(View.VISIBLE);
        } else {
            holder.providerBgIcon.setVisibility(View.GONE);
        }

    }

    @Override
    public void unbindView(MedicineViewHolder holder) {
        holder.icon.setImageDrawable(null);
        holder.icon.setContentDescription(null);
        holder.name.setText(null);
        holder.stockInfo.setText(null);
        holder.stockInfo.setVisibility(View.GONE);
        holder.alertIcon.setVisibility(View.GONE);
        holder.alertIcon.setImageDrawable(null);
        holder.alertIcon.setContentDescription(null);
        super.unbindView(holder);
    }

    @NonNull
    @Override
    public MedicineViewHolder getViewHolder(View v) {
        return new MedicineViewHolder(v);
    }

    public static class MedicineViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.imageButton)
        public ImageView icon;
        @BindView(R.id.medicines_list_item_name)
        public TextView name;
        @BindView(R.id.imageView)
        public ImageView alertIcon;
        @BindView(R.id.stock_info)
        public TextView stockInfo;
        @BindView(R.id.healthcare_provider_bg_icon)
        public IconicsImageView providerBgIcon;


        public MedicineViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
