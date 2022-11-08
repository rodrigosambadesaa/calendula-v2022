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

package es.usc.citius.servando.calendula.adapters;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.typeface.IIcon;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.fragments.ActiveMedicationListFragment;
import es.usc.citius.servando.calendula.fragments.DailyAgendaFragment;
import es.usc.citius.servando.calendula.fragments.MedicinesListFragment;
import es.usc.citius.servando.calendula.fragments.RoutinesListFragment;
import es.usc.citius.servando.calendula.fragments.ScheduleListFragment;
import es.usc.citius.servando.calendula.util.HealthcareProviderTypeface;

public enum HomePages {
    // attention: order is important!!!
    HOME(DailyAgendaFragment.class.getName(), R.string.app_name, GoogleMaterial.Icon.gmd_home, R.color.viewpager_default_background),
    ACTIVE_MEDICATION(ActiveMedicationListFragment.class.getName(), R.string.title_activity_active_medication_list, HealthcareProviderTypeface.Icon.hcp_healthcare_provider, R.color.healthcare_provider_light),
    MEDICINES(MedicinesListFragment.class.getName(), R.string.title_activity_medicines, CommunityMaterial.Icon2.cmd_pill, R.color.viewpager_default_background),
    ROUTINES(RoutinesListFragment.class.getName(), R.string.title_activity_routines, GoogleMaterial.Icon.gmd_alarm, R.color.viewpager_default_background),
    SCHEDULES(ScheduleListFragment.class.getName(), R.string.title_activity_schedules_reminders, GoogleMaterial.Icon.gmd_calendar, R.color.viewpager_default_background);

    public String className;
    public int title;
    public IIcon icon;
    @ColorInt
    public int backgroundColor;

    HomePages(String className, int title, IIcon icon, @ColorRes int backgroundColor) {
        this.className = className;
        this.title = title;
        this.icon = icon;
        this.backgroundColor = ContextCompat.getColor(CalendulaApp.getContext(), backgroundColor);
    }

    public static HomePages getPage(final int position) throws IndexOutOfBoundsException {
        return values()[position];
    }
}
