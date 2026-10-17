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
import android.os.Build
import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.security.verifier.Verification
import es.usc.citius.servando.calendula.util.security.verifier.VerificationResult


class InstallerVerification : Verification {

    companion object {
        private const val TAG = "IntegrityVerification"
        private const val GOOGLE_PLAY_INSTALLER = "com.android.vending"

        /** A package-name prefix is not evidence of a trusted installer. */
        internal fun isTrustedInstaller(installer: String?): Boolean =
            installer == GOOGLE_PLAY_INSTALLER
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
        val installer = installerPackageName(c)
        if (BuildConfig.DEBUG || isTrustedInstaller(installer)) {
            return true
        }
        LogUtil.w(TAG, "Invalid app installer")
        return false
    }

    private fun installerPackageName(c: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                c.packageManager.getInstallSourceInfo(c.packageName).installingPackageName
            } else {
                legacyInstallerPackageName(c)
            }
        } catch (e: Exception) {
            // Exception messages may include private package/file information.
            // Unknown install provenance is never trusted by the policy.
            LogUtil.w(TAG, "Could not resolve app install source")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyInstallerPackageName(c: Context): String? =
        c.packageManager.getInstallerPackageName(c.packageName)

}