/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    Calendula is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.login

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.activities.StartActivity
import es.usc.citius.servando.calendula.util.LogUtil


/**
 * Handles the configured logout redirect and returns to the normal app entry point.
 *
 * This activity is exported because a browser must be able to resolve the custom-scheme
 * callback. Treat every incoming Intent as untrusted and accept only the exact redirect
 * action/scheme/host configured for this build.
 */
class LogoutReceiveActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isExpectedLogoutRedirect(intent)) {
            LogUtil.w(TAG, "Ignoring unexpected external logout redirect")
            finish()
            return
        }

        startActivity(Intent(this, StartActivity::class.java))
        finish()
    }

    private fun isExpectedLogoutRedirect(incoming: Intent?): Boolean {
        if (incoming?.action != Intent.ACTION_VIEW) {
            return false
        }

        val uri = incoming.data ?: return false
        return uri.scheme.equals(BuildConfig.OAUTH_LOGOUT_REDIRECT_SCHEME, ignoreCase = true) &&
                uri.host.equals(BuildConfig.OAUTH_LOGOUT_REDIRECT_HOST, ignoreCase = true)
    }

    companion object {
        private const val TAG = "LogoutReceiveActivity"
    }
}
