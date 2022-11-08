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

package es.usc.citius.servando.calendula.activities;


import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.roomorama.caldroid.CaldroidFragment;
import com.roomorama.caldroid.CaldroidGridAdapter;

import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;
import es.usc.citius.servando.calendula.util.DispensationInfoStore;
import hirondelle.date4j.DateTime;

public class PickupCalendarAdapter extends CaldroidGridAdapter {

    private static final String notTakenSymbol = "●";
    private static final String takenSymbol = "✔";
    private static final String lostSymbol = "✘";

    private DispensationInfoStore dispensationInfo;

    public PickupCalendarAdapter(Context context, int month, int year,
                                 Map<String, Object> caldroidData,
                                 Map<String, Object> extraData, DispensationInfoStore dispensationInfo) {
        super(context, month, year, caldroidData, extraData);
        this.dispensationInfo = dispensationInfo;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View cellView = convertView;

        // For reuse
        if (convertView == null) {
            cellView = inflater.inflate(R.layout.custom_calendar_cell, null);
        }

        int topPadding = cellView.getPaddingTop();
        int leftPadding = cellView.getPaddingLeft();
        int bottomPadding = cellView.getPaddingBottom();
        int rightPadding = cellView.getPaddingRight();

        TextView tv1 = (TextView) cellView.findViewById(R.id.tv1);
        ImageView icon = (ImageView) cellView.findViewById(R.id.icon);

        tv1.setTextColor(Color.BLACK);

        // Get dateTime of this cell
        DateTime dateTime = this.datetimeList.get(position);
        Resources resources = context.getResources();

        // Set color of the dates in previous / next month
        if (dateTime.getMonth() != month) {
            tv1.setTextColor(resources.getColor(R.color.black_20));
        }

        boolean shouldResetDiabledView = false;
        boolean shouldResetSelectedView = false;

        // Customize for disabled dates and date outside min/max dates
        if ((minDateTime != null && dateTime.lt(minDateTime))
                || (maxDateTime != null && dateTime.gt(maxDateTime))
                || (disableDates != null && disableDates.indexOf(dateTime) != -1)) {

            tv1.setTextColor(CaldroidFragment.disabledTextColor);
            if (CaldroidFragment.disabledBackgroundDrawable == -1) {
                cellView.setBackgroundResource(com.caldroid.R.drawable.disable_cell);
            } else {
                cellView.setBackgroundResource(CaldroidFragment.disabledBackgroundDrawable);
            }

            if (dateTime.equals(today)) {
                cellView.setBackground(getTodayDrawable(R.color.android_blue));
            }

        } else {
            shouldResetDiabledView = true;
        }

        // Customize for selected dates
        if (selectedDates != null && selectedDates.indexOf(dateTime) != -1) {
            cellView.setBackgroundColor(resources.getColor(R.color.black_20));
            tv1.setTextColor(Color.BLACK);
        } else {
            shouldResetSelectedView = true;
        }

        if (shouldResetDiabledView && shouldResetSelectedView) {
            // Customize for today
            if (dateTime.equals(getToday())) {
                tv1.setTextColor(context.getResources().getColor(R.color.android_red));
            } else {
                cellView.setBackgroundResource(com.caldroid.R.drawable.cell_bg);
            }
        }

        tv1.setText(dateTime.getDay().toString());

        List<DispensationInfoEntity> starting = dispensationInfo.pickupsMap().get(new LocalDate(dateTime.getMilliseconds(TimeZone.getDefault())));
        List<DispensationInfoEntity> ending = new ArrayList<>();// dispensationInfo.pickupsEndMap().get(new LocalDate(dateTime.getMilliseconds(TimeZone.getDefault())));

        if (starting != null && starting.size() > 0) {
            boolean allTaken = true;
            for (DispensationInfoEntity infoEntity : starting) {
                if (!infoEntity.isDispensed()) {
                    allTaken = false;
                    break;
                }
            }
            icon.setBackground(getIntakeDrawable(allTaken));
            tv1.setTextColor(context.getResources().getColor(R.color.android_green));
        } else if (ending != null && ending.size() > 0) {
            tv1.setTextColor(context.getResources().getColor(R.color.android_green));
            icon.setBackground(getEndingDrawable());
        } else {
            icon.setBackgroundResource(R.color.transparent);
        }

        // Somehow after setBackgroundResource, the padding collapse.
        // This is to recover the padding
        cellView.setPadding(leftPadding, topPadding, rightPadding, bottomPadding);
        // Set custom color if required
        setCustomResources(dateTime, cellView, tv1);

        return cellView;
    }

    public Drawable getTodayDrawable(int color) {
        return new IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_checkbox_blank_circle_outline)
                .colorRes(color)
                .paddingDp(5)
                .sizeDp(20);
    }

    public Drawable getIntakeDrawable(boolean allTaken) {
        return new IconicsDrawable(context)
                .icon(allTaken ? CommunityMaterial.Icon.cmd_check : CommunityMaterial.Icon2.cmd_sync_alert)
                .colorRes(R.color.android_green)
                .paddingDp(2)
                .sizeDp(20);
    }

    public Drawable getEndingDrawable() {
        return new IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_calendar_clock)
                .colorRes(R.color.android_green)
                .paddingDp(2)
                .sizeDp(20);
    }
}
