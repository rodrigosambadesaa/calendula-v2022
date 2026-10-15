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

package es.usc.citius.servando.calendula.settings.notifications

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import es.usc.citius.servando.calendula.R
class RingtoneNameResolver(context: Context) {

    // Keep only the application Context, never a strong Activity reference.
    // A weak Activity Context may vanish before async ringtone UI refreshes.
    private val appContext = context.applicationContext ?: context

    fun resolveRingtoneName(uri: Uri?): String {
        if (uri == null) return appContext.getString(R.string.ringtone_none)
        // A ringtone can disappear from MediaStore, become inaccessible or
        // fail to resolve after a backup. The settings screen must stay usable.
        return resolvedTitleSafely {
            RingtoneManager.getRingtone(appContext, uri)?.getTitle(appContext)
        }
    }

    internal fun resolvedTitleSafely(loadTitle: () -> String?): String {
        val fallback = appContext.getString(R.string.ringtone_none)
        return try {
            loadTitle()?.takeIf { it.isNotBlank() } ?: fallback
        } catch (unavailable: RuntimeException) {
            // No exception text or ringtone URI in Logcat.
            fallback
        }
    }
}
