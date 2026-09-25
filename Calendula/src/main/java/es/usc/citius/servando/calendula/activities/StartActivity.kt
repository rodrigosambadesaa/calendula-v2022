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

package es.usc.citius.servando.calendula.activities


import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.login.LoginActivity
import es.usc.citius.servando.calendula.login.LogoutHelper
import es.usc.citius.servando.calendula.pinlock.PINManager
import es.usc.citius.servando.calendula.pinlock.PinLockActivity
import es.usc.citius.servando.calendula.pinlock.UnlockStateManager
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.security.verifier.AppVerifier
import es.usc.citius.servando.calendula.util.security.verifier.VerificationResult


class StartActivity : Activity() {

    companion object {
        const val EXTRA_RETURN_TO_PREVIOUS = "StartActivity.extras.return_to_previous"
        const val PREF_VULNERABILITY_WARNING_ACCEPTED = "vulnerability_warning_accepted_by_user"
    }

    private lateinit var alertIcon: IconicsDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alertIcon = IconicsDrawable(this)
            .icon(CommunityMaterial.Icon2.cmd_security)
            .colorRes(R.color.white)
            .sizeDp(130)
            .paddingDp(4)

        if (verifyApp()) {
            verifyUnlockAndLaunch()
        }
    }

    private fun verifyApp(): Boolean {
        val result = AppVerifier.performVerifications(applicationContext)
        when (result.level) {
            VerificationResult.Level.SECURITY_BREACH -> {
                showVulnerableEnvironmentMsg()
                return false
            }
            VerificationResult.Level.SECURITY_WARNING -> {
                if (!PreferenceUtils.instance().preferences().getBoolean(
                        PREF_VULNERABILITY_WARNING_ACCEPTED,
                        false
                    )) {
                    showWarningEnvironmentMsg(result)
                    return false
                }
                return true
            }
            VerificationResult.Level.SECURITY_OK -> return true
        }
    }

    private fun showWarningEnvironmentMsg(r: VerificationResult) {
        showAlertDialog(
            getString(R.string.vulnerability_warning_dialog_title),
            getString(R.string.vulnerability_warning_dialog_description) + if(r.message.isNotEmpty()) "\n" + r.message else "",
            { dialog, _ ->
                PreferenceUtils.edit().putBoolean(PREF_VULNERABILITY_WARNING_ACCEPTED, true).apply()
                dialog.dismiss()
                verifyUnlockAndLaunch()
            }, { dialog, _ ->
                dialog.dismiss()
                finish()
            }
        )
    }

    private fun showVulnerableEnvironmentMsg() {
        showAlertDialog(
            getString(R.string.vulnerable_environment_dialog_title),
            getString(R.string.vulnerable_environment_dialog_description) + "\n",
            { dialog, _ ->
                dialog.dismiss()
                // Clear app data and finish. clearApplicationUserData() was added in API 19;
                // keep the API-18 compatibility contract with the app's own secure cleanup.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    (application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                        .clearApplicationUserData()
                } else {
                    LogoutHelper.clearData(applicationContext)
                }
                finish()
            }, null
        )
    }

    private fun showAlertDialog(
        title: String,
        description: String,
        positive: (MaterialDialog, DialogAction) -> Unit,
        negative: ((MaterialDialog, DialogAction) -> Unit)?
    ) {
        val builder = MaterialStyledDialog.Builder(this)
            .setIcon(alertIcon)
            .setTitle(title)
            .setDescription(description)
            .setCancelable(false)
            .setPositiveText(getString(R.string.dialog_continue_option))
            .onPositive(positive)

        if (negative != null) {
            builder.onNegative(negative)
            builder.setNegativeText(getString(R.string.dialog_get_me_out))
        }

        builder.build().show()
    }


    private fun verifyUnlockAndLaunch() {
        if (PINManager.isPINSet() && !UnlockStateManager.getInstance().isUnlocked) {
            val i = Intent(this, PinLockActivity::class.java)
            i.action = PinLockActivity.ACTION_VERIFY_PIN
            startActivityForResult(i, PinLockActivity.REQUEST_VERIFY)
        } else {
            val returnToPrevious = intent.getBooleanExtra(EXTRA_RETURN_TO_PREVIOUS, false)
            if (!returnToPrevious) {
                // if "return to previous" is specified, just finish this activity to go back in the stack
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PinLockActivity.REQUEST_VERIFY) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish()
            } else {
                verifyUnlockAndLaunch()
            }
        }
    }
}
