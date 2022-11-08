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

package es.usc.citius.servando.calendula.util;

import android.app.Activity;
import androidx.test.espresso.Espresso;
import android.view.WindowManager;

import java.sql.SQLException;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;

public class TestUtils {

    private static final String TAG = "TestUtils";

    public static void resetDatabase() {
        DB.dropAndCreateDatabase();
        try {
            final Patient defaultPatient = DB.helper().createDefaultPatient();
            DB.patients().setActive(defaultPatient);
        } catch (SQLException e) {
            LogUtil.e(TAG, "resetDatabase: ", e);
        }
    }

    public static void closeKeyboard() {
        Espresso.closeSoftKeyboard();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
    }

    public static void sleep(int ms) {
        Espresso.closeSoftKeyboard();
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }


    public static void unlockScreen(final Activity activity){
        Runnable wakeUpDevice = new Runnable() {
            public void run() {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        };
        activity.runOnUiThread(wakeUpDevice);
    }

}
