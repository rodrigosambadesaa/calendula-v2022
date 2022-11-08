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
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.security.verifier.Verification
import es.usc.citius.servando.calendula.util.security.verifier.VerificationResult


class EmulatorVerification : Verification {

    companion object {
        private const val TAG = "EmulatorVerification"
    }

    override fun run(c: Context): VerificationResult {
        if(isEmulator()) {
            return VerificationResult(
                VerificationResult.Level.SECURITY_BREACH
            )
        }
        return VerificationResult.ok()
    }

    /**
     * Check if app is running in an emulator
     */
    private fun isEmulator(): Boolean {

        val isEmulator = Build.FINGERPRINT.startsWith("generic", true)
                || Build.FINGERPRINT.startsWith("unknown", true)
                || Build.MODEL.contains("google_sdk", true)
                || Build.MODEL.contains("Emulator", true)
                || Build.MODEL.contains("Android SDK built for x86", true)
                || Build.MANUFACTURER.contains("Genymotion", true)
                || (Build.BRAND.startsWith("generic", true) && Build.DEVICE.startsWith("generic", true))
                || Build.PRODUCT == "google_sdk"

        if(isEmulator) {
            LogUtil.w(TAG, "App is running in an emulator")
            return true
        }
        return false
    }


}