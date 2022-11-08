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

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.security.verifier.Verification
import es.usc.citius.servando.calendula.util.security.verifier.VerificationResult
import java.security.MessageDigest


class SignatureVerification : Verification {

    companion object {
        private const val TAG = "IntegrityVerification"

        private const val PRODUCTION_FINGERPRINT = "2+x+5KyEupK+9NZRlOvfsBKdkjw="
        private const val DEBUG_FINGERPRINT = "doYNGlfTZkpbUY3BssUz/6IPZNI="
    }

    override fun run(c: Context): VerificationResult {
        if (!verifyAppSignature(c)) {
            return VerificationResult(
                    VerificationResult.Level.SECURITY_BREACH
            )
        }
        return VerificationResult.ok()
    }


    /**
     * Check if the app certificate fingerprint is valid
     */

    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures")
    private fun verifyAppSignature(c: Context): Boolean {

        val signatures =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val info = c.packageManager.getPackageInfo(c.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                    if (info.hasMultipleSigners()) {
                        info.apkContentsSigners
                    } else {
                        info.signingCertificateHistory
                    }
                } else {
                    val info = c.packageManager.getPackageInfo(c.packageName, PackageManager.GET_SIGNATURES)
                    info.signatures

                }
        val validSignatures = getValidSignatures()
        signatures.forEach {
            val md = MessageDigest.getInstance("SHA")
            md.update(it.toByteArray())
            val current = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
            if (!validSignatures.contains(current)) {
                LogUtil.w(TAG, "Invalid app signature")
                return false
            }
        }

        return true
    }

    private fun getValidSignatures(): List<String> {
        // add production cert fingerprint to valid signatures
        val validSignatures = mutableListOf(PRODUCTION_FINGERPRINT)
        if (BuildConfig.DEBUG) {
            validSignatures.add(DEBUG_FINGERPRINT)  // Debug cert fingerprint
        }
        return validSignatures
    }

}