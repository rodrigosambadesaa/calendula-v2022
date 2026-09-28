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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.fragment.app.Fragment;

/**
 * Android permission utility class for the app's Android 6.0+ baseline.
 */
public class PermissionUtils {

    public static boolean hasPermission(Activity activity, String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermissions(Activity activity, String[] permission, int requestCode) {
        activity.requestPermissions(permission, requestCode);
    }

    public static void requestPermissions(Fragment fragment, String[] permission, int requestCode) {
        fragment.requestPermissions(permission, requestCode);
    }

    public static boolean shouldShowRational(Activity activity, String permission) {
        return activity.shouldShowRequestPermissionRationale(permission);
    }

    public static boolean shouldAskForPermission(Activity activity, String permission) {
        return !hasPermission(activity, permission)
                && (!hasAskedForPermission(activity, permission)
                || shouldShowRational(activity, permission));
    }

    public static void goToAppSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.getPackageName(), null));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    public static boolean hasAskedForPermission(Activity activity, String permission) {
        return PreferenceUtils.instance().preferences()
                .getBoolean("asked-for" + permission, false);
    }

    public static void markPermissionAsAsked(Activity activity, String permission) {
        PreferenceUtils
                .edit()
                .putBoolean("asked-for" + permission, true)
                .apply();
    }

    public interface PermissionRequest {
        int reqCode();

        String[] permissions();

        void onPermissionGranted();

        void onPermissionDenied();
    }
}
