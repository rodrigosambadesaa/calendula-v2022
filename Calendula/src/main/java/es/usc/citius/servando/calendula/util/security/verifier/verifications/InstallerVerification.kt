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
import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.security.verifier.Verification
import es.usc.citius.servando.calendula.util.security.verifier.VerificationResult


class InstallerVerification : Verification {

    companion object {
        private const val TAG = "IntegrityVerification"
    }

    override fun run(c: Context): VerificationResult {
        if (!verifyInstaller(c)) {
            return VerificationResult(
                VerificationResult.Level.SECURITY_BREACH,
                "Invalid app installer"
            )
        }
        return VerificationResult.ok()
    }

    /**
     * Verifies thar the app installer is Google Play (or null in DEBUG)
     */
    private fun verifyInstaller(c: Context): Boolean {
        val installer = c.packageManager.getInstallerPackageName(c.packageName)
        if (BuildConfig.DEBUG || (installer != null && installer.startsWith("com.android.vending"))) {
            return true
        }
        LogUtil.w(TAG, "Invalid app installer '$installer'")
        return false
    }

}