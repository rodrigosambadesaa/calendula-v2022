/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2017 CITIUS - USC
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
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.login;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import javax.net.ssl.SSLContext;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.HomePagerActivity;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.Snack;

public class LoginActivity extends AppCompatActivity {

    public static final String ACTION_LOGIN_FAILED = "Calendula.LoginActivity.ACTION_LOGIN_FAILED";
    public static final String EXTRA_LOGIN_FAILED_REASON = "Calendula.LoginActivity.EXTRA_LOGIN_FAILED_REASON";

    public static final String LOGIN_FAIL_REASON_AUTH_LEVEL = "AUTH_LEVEL";

    private static final String TAG = "LoginActivity";

    @BindView(R.id.login_button)
    Button loginBtn;
    @BindView(R.id.tv_no_login)
    TextView skipLoginText;

    private AuthorizationService mAuthService;


    @OnClick(R.id.tv_no_login)
    void continueWithoutLogin() {
        if (BuildConfig.DEBUG) {
            Patient patient = DB.patients().getDefault();
            if (patient == null) {
                patient = new Patient();
                patient.setName("Default");
                patient.setDefault(true);
                try {
                    DB.patients().create(patient);
                } catch (SQLException e) {
                    LogUtil.e(TAG, "continueWithoutLogin: ", e);
                }
                DB.patients().setActive(patient);
            }
            LoginStateManager.getInstance().skipLogin();
            startActivity(new Intent(this, HomePagerActivity.class));
            finish();
        }
    }

    @OnClick(R.id.login_button)
    void doLogin() {
        loginBtn.setEnabled(false);
        makeAuthRequest(OpenIdProviderConfiguration.instance().getConfiguration(), new AuthState());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String action = getIntent().getAction();

        if (action == null && !LoginStateManager.getInstance().shouldAskForLogin()) {
            LogUtil.d(TAG, "onCreate: user is already logged in. Forwarding to home activity");
            startActivity(new Intent(this, HomePagerActivity.class));
            finish();
        } else {

            setContentView(R.layout.activity_login);
            ButterKnife.bind(this);

            if (BuildConfig.DEBUG) {
                skipLoginText.setVisibility(View.VISIBLE);
            }

            mAuthService = AuthorizationServiceHelper.createAuthorizationService(this);

            if (action != null && action.equals(ACTION_LOGIN_FAILED)) {
                final String failReason = getIntent().getStringExtra(EXTRA_LOGIN_FAILED_REASON);
                if (failReason != null && failReason.equals(LOGIN_FAIL_REASON_AUTH_LEVEL)) {
                    // Save login failed status
                    PreferenceUtils.edit().putBoolean(PreferenceKeys.LAST_LOGIN_FAILED_AUTH_LEVEL.key(), true).commit();
                } else {
                    Snack.show(getString(R.string.login_failed) + " [" + failReason + "]", this);
                }
            }

            // check google play services availability
            checkGooglePlayServicesAvailability();
            checkSSL();
        }
    }

    private void checkGooglePlayServicesAvailability() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                Dialog dialog = apiAvailability.getErrorDialog(this, resultCode, 12345);
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                });
                dialog.show();
            } else {
                apiAvailability.showErrorNotification(this, resultCode);
                LogUtil.i(TAG, "Play services not available, device not supported.");
                finish();
            }
        }
    }

    private void checkSSL() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                ProviderInstaller.installIfNeeded(getApplicationContext());
                SSLContext sslContext;
                sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(null, null, null);
                sslContext.createSSLEngine();
            } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException
                    | NoSuchAlgorithmException | KeyManagementException e) {
                LogUtil.e(TAG, "System does not support TLSv1.2, device not supported.", e);
                showInvalidSSLDialog();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtil.d(TAG, "onResume");
        showInvalidAuthLevelDialogIfNeeded();
    }

    private void showInvalidSSLDialog(){

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                IconicsDrawable icon = new IconicsDrawable(LoginActivity.this)
                        .icon(CommunityMaterial.Icon2.cmd_security)
                        .colorRes(R.color.white)
                        .sizeDp(130)
                        .paddingDp(4);
                    new MaterialStyledDialog.Builder(LoginActivity.this)
                            .setIcon(icon)
                            .setTitle(R.string.login_failed_invalid_SSL_version_title)
                            .setDescription(R.string.login_failed_invalid_SSL_version)
                            .setCancelable(false)
                            .setPositiveText(R.string.ok)
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    dialog.dismiss();
                                    finish();
                                }
                            })
                            .build().show();
            }
        });
    }

    private void showInvalidAuthLevelDialogIfNeeded(){

        final boolean authLevelFailed = PreferenceUtils.getBoolean(PreferenceKeys.LAST_LOGIN_FAILED_AUTH_LEVEL, false);
        LogUtil.d(TAG, "showInvalidAuthLevelDialogIfNeeded: " + authLevelFailed);

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if(authLevelFailed){
                    new MaterialStyledDialog.Builder(LoginActivity.this)
                            .setTitle(R.string.login_failed_auth_level_title)
                            .setDescription(R.string.login_failed_auth_level)
                            .setCancelable(false)
                            .setPositiveText(R.string.ok)
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    PreferenceUtils.edit().putBoolean(PreferenceKeys.LAST_LOGIN_FAILED_AUTH_LEVEL.key(), false).commit();
                                    dialog.dismiss();
                                    LoginStateManager.getInstance().logout(LoginActivity.this.getApplicationContext(), false);
                                }
                            })
                            .build().show();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAuthService != null) {
            mAuthService.dispose();
        }
    }

    private void makeAuthRequest(
            @NonNull AuthorizationServiceConfiguration serviceConfig,
            @NonNull AuthState authState) {

        String loginHint = getString(R.string.openid_login_hint);

        try {
            final OpenIdProviderConfiguration openIdProviderConfiguration = OpenIdProviderConfiguration.instance();

            final AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(
                    serviceConfig,
                    openIdProviderConfiguration.getClientId(),
                    ResponseTypeValues.CODE,
                    OpenIdConstants.REDIRECT_URI)
                    .setLoginHint(loginHint)
                    .setAdditionalParameters(InstanceIDHelper.getAdditionalParams(this));


            final String scope = openIdProviderConfiguration.getScope();
            if (!TextUtils.isEmpty(scope)) {
                builder.setScope(scope);
            }

            AuthorizationRequest authRequest = builder.build();

            LogUtil.d(TAG, "Making auth request to " + serviceConfig.authorizationEndpoint);
            mAuthService.performAuthorizationRequest(
                    authRequest,
                    LoginReceiveActivity.createPostAuthorizationIntent(
                            this,
                            authRequest,
                            serviceConfig.discoveryDoc,
                            authState),

                    mAuthService.createCustomTabsIntentBuilder()
                            .setToolbarColor(ContextCompat.getColor(this, R.color.healthcare_provider_dark))
                            .build());
            finish();
        } catch (InstanceIDHelper.NoInstanceIDException e) {
            onInstanceIDError(e);
        }
    }


    private void onInstanceIDError(final InstanceIDHelper.NoInstanceIDException exception) {
        LogUtil.e(TAG, "onInstanceIDError: ", exception);
        switch (exception.getReason()) {
            case NO_CONNECTION:
                Snack.showIfUnobstructed(R.string.login_no_internet, this, Snackbar.LENGTH_LONG);
                break;
            case UNSPECIFIED:
                Snack.showIfUnobstructed(getString(R.string.login_failed) + " [INSTANCE_ID]", this, Snackbar.LENGTH_LONG);
                break;
        }
        loginBtn.setEnabled(true);
    }
}
