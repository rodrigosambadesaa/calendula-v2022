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
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.healthcareprovider.modules;

import android.content.Context;

import es.usc.citius.servando.calendula.modules.CalendulaModule;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * Logs out the active patient on boot, if the relevant property is set
 */
public class RemoveActivePatientOnBootModule extends CalendulaModule {

    public static final String ID = "CALENDULA_REMOVE_ACTIVE_PATIENT_MODULE";

    private static final String TAG = "RemoveActivePatientOnBo";

    @Override
    public String getId() {
        return ID;
    }

    /**
     * An incompatible restored deferred-logout marker must not retain the
     * previously active patient. Treat corruption as a pending logout, never
     * as a reason to bypass it or abort the entire app's initialization.
     */
    static boolean shouldRemoveActivePatient() {
        try {
            return PreferenceUtils.getBoolean(
                    PreferenceKeys.PATIENTS_REMOVE_ACTIVE_ON_BOOT, false);
        } catch (ClassCastException incompatible) {
            return true;
        }
    }

    @Override
    protected void onApplicationStartup(Context ctx) {
        if (shouldRemoveActivePatient()) {
            LogUtil.d(TAG, "onApplicationStartup: Key is set, removing active patient");
            PreferenceUtils.edit()
                    .remove(PreferenceKeys.PATIENTS_ACTIVE.key())
                    .remove(PreferenceKeys.PATIENTS_REMOVE_ACTIVE_ON_BOOT.key())
                    .commit();
        }
    }
}
