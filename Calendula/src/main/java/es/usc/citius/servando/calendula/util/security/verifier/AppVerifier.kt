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

package es.usc.citius.servando.calendula.util.security.verifier

import android.annotation.SuppressLint
import android.content.Context
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.security.SecuredVault
import es.usc.citius.servando.calendula.util.security.verifier.VerificationResult.Level
import es.usc.citius.servando.calendula.util.security.verifier.verifications.*


object AppVerifier {

    private const val TAG = "AppVerifier"

    private val verifications: Collection<Verification> = arrayListOf(
//            RootVerification(),
//            DebugVerification(),
//            EmulatorVerification(),
//            SignatureVerification(),
//            InstallerVerification(),
//            RuntimeVerification()
    )

    fun performVerifications(c: Context): VerificationResult {

        val results = verifications.map {
            val result = it.run(c)
            LogUtil.d(TAG, it.javaClass.simpleName + ": " + result.level)
            result
        }

        val breaches = results.filter { it.level == Level.SECURITY_BREACH }
        val warnings = results.filter { it.level == Level.SECURITY_WARNING }

        return when {
            breaches.isNotEmpty() -> {
                onSecurityBreach()
                VerificationResult(Level.SECURITY_BREACH, composeMessage(breaches))
            }
            warnings.isNotEmpty() -> {
                VerificationResult(Level.SECURITY_WARNING, composeMessage(warnings))
            }
            else -> VerificationResult(Level.SECURITY_OK)
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun onSecurityBreach() {
        // Drop all data from db
        DB.helper().dropAndCreateAllTables()
        // Clear secure vault
        SecuredVault.edit().clear().commit()
        // Clear preferences
        PreferenceUtils.edit().clear().commit()
    }

    private fun composeMessage(v: Collection<VerificationResult>): String {
        return v.joinToString { if (it.message.isNotEmpty()) "\n ● ${it.message}" else "" }
    }
}