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
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.HealthcareProviderTypeface;


public class ScheduleListItem extends AbstractItem<ScheduleListItem, ScheduleListItem.ScheduleViewHolder> {

    private static final String TAG = "ScheduleListItem";
    private final Schedule schedule;

    public ScheduleListItem(Schedule s) {
        this.schedule = s;
    }


    public Schedule getSchedule() {
        return schedule;
    }

    @Override
    public int getType() {
        return R.id.fastadapter_schedule_item;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.schedules_list_item;
    }

    @Override
    public boolean isSelectable() {
        return false;
    }

    @Override
    public void bindView(ScheduleViewHolder holder, List<Object> payloads) {
        super.bindView(holder, payloads);

        Context ctx = holder.itemView.getContext();
        String timeStr = schedule.intakeTimesString(ctx);
        String daysStr = schedule.intakeDaysString(ctx);

        LogUtil.d(TAG, "Schedule " + schedule.getMedicine().getName() + " is scanned: " + schedule.isScanned());

        if (schedule.isBoundToActiveMed() && schedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
            int color = schedule.hasState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL) ?
                    R.color.android_orange_dark :
                    R.color.android_green;

            holder.info.setVisibility(View.VISIBLE);
            holder.info.setImageDrawable(new IconicsDrawable(ctx)
                    .icon(HealthcareProviderTypeface.Icon.hcp_healthcare_provider_background)
                    .colorRes(color)
                    .alpha(110)
                    .paddingDp(0)
                    .sizeDpX(80)
                    .sizeDpY(60));
        }else{
            holder.info.setVisibility(View.INVISIBLE);
        }

        holder.icon2.setImageDrawable(new IconicsDrawable(ctx)
                .icon(schedule.getMedicine().getPresentation().icon())
                .color(Color.WHITE)
                .paddingDp(8)
                .sizeDp(40));

        IIcon i = schedule.getRecur().hasHourlyFrequency() ? CommunityMaterial.Icon2.cmd_history : CommunityMaterial.Icon.cmd_clock;

        holder.icon.setImageDrawable(new IconicsDrawable(ctx)
                .icon(i)
                .colorRes(R.color.agenda_item_title)
                .paddingDp(8)
                .sizeDp(40));

        holder.medName.setText(schedule.getMedicine().getName());
        holder.itemTimes.setText(timeStr);
        holder.itemDays.setText(daysStr);

        if(schedule.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE) ||
                schedule.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_DELETE)){
            holder.moreInfo.setVisibility(View.VISIBLE);
            holder.moreInfo.setText(R.string.reminder_pending_revision);
        }else{
            holder.moreInfo.setVisibility(View.GONE);
        }
    }

    @Override
    public void unbindView(ScheduleViewHolder holder) {
        super.unbindView(holder);
    }

    @NonNull
    @Override
    public ScheduleViewHolder getViewHolder(View v) {
        return new ScheduleViewHolder(v);
    }

    public static class ScheduleViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.imageButton)
        ImageView icon;
        @BindView(R.id.imageView)
        ImageView icon2;
        @BindView(R.id.schedules_list_item_medname)
        TextView medName;
        @BindView(R.id.schedules_list_item_times)
        TextView itemTimes;
        @BindView(R.id.schedules_list_item_days)
        TextView itemDays;
        @BindView(R.id.schedules_list_item_more_info)
        TextView moreInfo;
        @BindView(R.id.info_img)
        ImageView info;

        public ScheduleViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
