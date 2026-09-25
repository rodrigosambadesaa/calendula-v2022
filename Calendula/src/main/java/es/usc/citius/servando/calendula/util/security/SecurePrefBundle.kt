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

package es.usc.citius.servando.calendula.util.security

import es.usc.citius.servando.calendula.util.GsonUtil
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PreferenceKeys

/**
 * Manage access and modification of a bundle of secrets
 * stored together in encrypted shared preferences
 */
object SecurePrefBundle {

    private const val TAG = "SecurePrefBundle";

    private val bundle: Bundle by lazy {
        val secrets = SecuredVault.getString(PreferenceKeys.SECURE_PREF_BUNDLE.key(), null)
        if (secrets != null) {
            GsonUtil.get().fromJson(secrets, Bundle::class.java)
        } else {
            Bundle()
        }
    }

    fun setAuthState(state: String): SecurePrefBundle {
        bundle.authState = state
        return this
    }

    fun setPinHash(hash: String): SecurePrefBundle {
        bundle.pinHash = hash
        return this
    }

    fun setPinSalt(salt: String): SecurePrefBundle {
        bundle.pinSalt = salt
        return this
    }

    fun setInstanceId(instanceId: String): SecurePrefBundle {
        bundle.instanceId = instanceId
        return this
    }

    fun setPatientLinkToken(patientId: Long, token: String): SecurePrefBundle {
        if (bundle.patientLinkTokens == null) {
            bundle.patientLinkTokens = mutableMapOf()
        }
        bundle.patientLinkTokens!![patientId.toString()] = token
        return this
    }

    fun clearAuthState(): SecurePrefBundle {
        bundle.authState = null
        return this
    }

    fun clearPinHash(): SecurePrefBundle {
        bundle.pinHash = null
        return this
    }

    fun clearPinSalt(): SecurePrefBundle {
        bundle.pinSalt = null
        return this
    }

    fun clearInstanceId(): SecurePrefBundle {
        bundle.instanceId = null
        return this
    }

    fun clearPatientLinkToken(patientId: Long): SecurePrefBundle {
        bundle.patientLinkTokens?.remove(patientId.toString())
        return this
    }

    fun getAuthState(): String? = bundle.authState
    fun getPinSalt(): String? = bundle.pinSalt
    fun getPinHash(): String? = bundle.pinHash
    fun getInstanceId(): String? = bundle.instanceId
    fun getPatientLinkToken(patientId: Long): String? =
        bundle.patientLinkTokens?.get(patientId.toString())

    /**
     * Persists the current bundle values to secured shared preferences
     */
    fun apply() {
        SecuredVault.edit().putString(PreferenceKeys.SECURE_PREF_BUNDLE.key(), serialize()).apply()
    }


    /**
     * Removes the bundle from secured shared preferences
     */
    fun delete() {
        SecuredVault.edit().remove(PreferenceKeys.SECURE_PREF_BUNDLE.key()).apply()
        clearAuthState()
        clearPinHash()
        clearPinSalt()
        clearInstanceId()
        bundle.patientLinkTokens?.clear()
        bundle.patientLinkTokens = null
    }

    /**
     * Convert the Bundle to json for storage
     */
    private fun serialize(): String {
        try {
            return GsonUtil.get().toJson(bundle)
        } catch (e: Exception) {
            LogUtil.e(TAG, "serialize: failed to serialize bundle", e)
            throw e
        }
    }

    /**
     * Bundle with the secrets that are going to be encrypted
     */
    private data class Bundle(val auth: String? = null) {
        var authState: String? = null
        var pinSalt: String? = null
        var pinHash: String? = null
        var instanceId: String? = null
        var patientLinkTokens: MutableMap<String, String>? = null
    }


}