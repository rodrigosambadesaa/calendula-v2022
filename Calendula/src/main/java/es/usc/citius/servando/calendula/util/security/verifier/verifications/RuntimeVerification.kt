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
import android.content.pm.PackageManager
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.security.verifier.Verification
import es.usc.citius.servando.calendula.util.security.verifier.VerificationResult

/**
 * https://d3adend.org/blog/?p=589
 */
class RuntimeVerification : Verification {

    companion object {
        private const val TAG = "RuntimeVerification"

        // class names to inspect
        private const val ZYGOTE = "com.android.internal.os.ZygoteInit"
        private const val SUBSTRATE = "com.saurik.substrate.MS$2"
        private const val XPOSED = "de.robv.android.xposed.XposedBridge"

        private val SUSPICIOUS_APPS = listOf(
            "com.saurik.substrate",
            "de.robv.android.xposed.installer"
        )
    }

    override fun run(c: Context): VerificationResult {
        if (suspiciousAppsFound(c) || stackTraceHooked()) {
            return VerificationResult(
                VerificationResult.Level.SECURITY_OK
            )
        }
        return VerificationResult.ok()
    }


    private fun suspiciousAppsFound(c: Context): Boolean {
        val apps = c.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (applicationInfo in apps) {
            if (SUSPICIOUS_APPS.contains(applicationInfo.packageName)) {
                return true
            }
        }
        return false
    }

    private fun stackTraceHooked(): Boolean {
        try {
            throw Exception("Give me an stack trace")
        } catch (e: Exception) {

            var zygoteInitCallCount = 0

            for (stackTraceElement in e.stackTrace) {
                val className = stackTraceElement.className
                val methodName = stackTraceElement.methodName
                when {
                    className == ZYGOTE -> {
                        zygoteInitCallCount++
                        if (zygoteInitCallCount == 2) {
                            return true
                        }
                    }
                    // A method on the stack trace has been hooked using Substrate
                    className == SUBSTRATE && methodName == "invoked" -> return true
                    // A method on the stack trace has been hooked using xposed
                    className == XPOSED && methodName == "main" -> return true
                    // A method on the stack trace has been hooked using xposed
                    className == XPOSED && methodName == "handleHookedMethod" -> return true
                }
            }
            // no hooks found
            return false
        }
    }

}