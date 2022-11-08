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

package es.usc.citius.servando.calendula;

import android.content.Intent;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.view.View;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import es.usc.citius.servando.calendula.activities.LeftDrawerMgr;
import es.usc.citius.servando.calendula.activities.MedicinesActivity;
import es.usc.citius.servando.calendula.activities.RoutinesActivity;
import es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity;
import es.usc.citius.servando.calendula.adapters.HomePages;
import es.usc.citius.servando.calendula.persistence.Patient;

/**
 * Helper to manage the home screen floating action button behaviour
 */
public class FabMenuMgr implements View.OnClickListener {


    LeftDrawerMgr drawerMgr;
    FloatingActionButton fab;
    HomePagerActivity activity;

    private int currentPage = 0;


    public FabMenuMgr(FloatingActionButton fab, LeftDrawerMgr drawerMgr, HomePagerActivity a) {
        this.fab = fab;
        this.activity = a;
        this.drawerMgr = drawerMgr;
    }

    public void init() {
        fab.setOnClickListener(this);
        fab.setImageDrawable(new IconicsDrawable(activity)
                .icon(GoogleMaterial.Icon.gmd_plus)
                .paddingDp(5)
                .sizeDp(24)
                .colorRes(R.color.fab_default_icon_color));
        onViewPagerItemChange(0);
    }

    public void onViewPagerItemChange(int currentPage) {

        this.currentPage = currentPage;
        HomePages page = HomePages.values()[currentPage];

        switch (page) {
            case HOME:
            case ACTIVE_MEDICATION:
                fab.hide();
                break;
            case ROUTINES:
            case MEDICINES:
            case SCHEDULES:
                fab.show();
                break;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.add_button:
                onClickAdd();
                break;
        }
    }

    public void onPatientUpdate(Patient p) {

    }

    private void onClickAdd() {
        HomePages page = HomePages.values()[currentPage];
        switch (page) {
            case HOME:
                return;
            case ROUTINES:
                launchActivity(RoutinesActivity.class);
                break;
            case MEDICINES:
                launchActivity(MedicinesActivity.class);
                break;
            case SCHEDULES:
                launchActivity(ScheduleBuildActivity.class);
                break;
        }
    }

    private void launchActivity(Class<?> type) {
        activity.startActivity(new Intent(activity, type));
        activity.overridePendingTransition(0, 0);
    }
}
