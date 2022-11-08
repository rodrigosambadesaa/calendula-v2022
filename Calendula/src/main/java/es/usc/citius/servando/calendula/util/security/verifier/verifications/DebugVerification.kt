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
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.util.security.verifier.verifications

import android.content.Context
import android.content.pm.ApplicationInfo
import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.security.verifier.Verification
import es.usc.citius.servando.calendula.util.security.verifier.VerificationResult


class DebugVerification : Verification {

    companion object {
        private const val TAG = "DebugVerification"
    }

    override fun run(c: Context): VerificationResult {
        if (isDebuggable(c)) {
            return VerificationResult(
                VerificationResult.Level.SECURITY_BREACH
            )
        }
        return VerificationResult.ok()
    }

    /**
     * Check if app is debuggable
     */
    private fun isDebuggable(c: Context): Boolean {
        if (!BuildConfig.DEBUG && (c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            return true
        }
        LogUtil.w(TAG, "App debuggable")
        return false
    }


}