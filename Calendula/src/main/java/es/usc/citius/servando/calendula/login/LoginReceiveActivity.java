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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.NotificationManagerCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceDiscovery;
import net.openid.appauth.ClientAuthentication;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;

import org.json.JSONException;

import java.util.Map;
import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.HomePagerActivity;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.StartActivity;
import es.usc.citius.servando.calendula.healthcareprovider.jobs.UpdateMedicationHelper;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
 * A sample activity to serve as a client to the Native Oauth library.
 */
public class LoginReceiveActivity extends AppCompatActivity {
    private static final String TAG = "LoginReceiveActivity";

    private static final String KEY_AUTH_STATE = "authState";
    private static final String KEY_USER_INFO = "userInfo";

    private static final String EXTRA_AUTH_SERVICE_DISCOVERY = "authServiceDiscovery";
    private static final String EXTRA_AUTH_STATE = "authState";
    public static final String EXTRA_RESPONSE = "net.openid.appauth.AuthorizationResponse";

    private static final int BUFFER_SIZE = 1024;

    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
    @BindView(R.id.logging_in_layout)
    View loggingInLayout;
    @BindView(R.id.logged_in_layout)
    View loggedInLayout;

    private AuthState mAuthState;
    private AuthorizationService mAuthService;

    static PendingIntent createPostAuthorizationIntent(
            @NonNull Context context,
            @NonNull AuthorizationRequest request,
            @Nullable AuthorizationServiceDiscovery discoveryDoc,
            @NonNull AuthState authState) {
        Intent intent = new Intent(context, LoginReceiveActivity.class);
        intent.putExtra(EXTRA_AUTH_STATE, authState.jsonSerializeString());
        if (discoveryDoc != null) {
            intent.putExtra(EXTRA_AUTH_SERVICE_DISCOVERY, discoveryDoc.docJson.toString());
        }

        return PendingIntent.getActivity(context, request.hashCode(), intent, 0);
    }


    static AuthState getAuthStateFromIntent(Intent intent) {
        if (!intent.hasExtra(EXTRA_AUTH_STATE)) {
            throw new IllegalArgumentException("The AuthState instance is missing in the intent.");
        }
        try {
            return AuthState.jsonDeserialize(intent.getStringExtra(EXTRA_AUTH_STATE));
        } catch (JSONException ex) {
            LogUtil.e(TAG, "Malformed AuthState JSON saved", ex);
            throw new IllegalArgumentException("The AuthState instance is missing in the intent.");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_receive);
        ButterKnife.bind(this);

        progressBar.getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY);
        mAuthService = AuthorizationServiceHelper.createAuthorizationService(this);

        if (BuildConfig.DEBUG) {
            LogUtil.d(TAG,  "AUTH_STATE: " + getIntent().getStringExtra(EXTRA_AUTH_STATE));
            LogUtil.d(TAG,  "AUTH_RESPONSE: " + getIntent().getStringExtra(EXTRA_RESPONSE));
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_AUTH_STATE)) {
                try {
                    mAuthState = AuthState.jsonDeserialize(
                            savedInstanceState.getString(KEY_AUTH_STATE));
                } catch (JSONException ex) {
                    LogUtil.e(TAG, "Malformed authorization JSON saved", ex);
                }
            }
        }

        if (mAuthState == null) {
            mAuthState = getAuthStateFromIntent(getIntent());
            AuthorizationResponse response = AuthorizationResponse.fromIntent(getIntent());
            AuthorizationException ex = AuthorizationException.fromIntent(getIntent());
            mAuthState.update(response, ex);

            if (response != null) {
                LogUtil.d(TAG, "Received AuthorizationResponse.");
                exchangeAuthorizationCode(response);
            } else {
                LogUtil.i(TAG, "Authorization failed: " + ex);
                onLoginFailed("AUTH_FAILED");
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAuthService != null)
            mAuthService.dispose();
    }

    private void receivedTokenResponse(
            @Nullable final TokenResponse tokenResponse,
            @Nullable final AuthorizationException authException, final boolean force) {
        LogUtil.d(TAG, "Token request complete");
        mAuthState.update(tokenResponse, authException);


        if (tokenResponse != null) {
            final LoginStateManager.LoginResult loginResult = LoginStateManager.getInstance().login(this, mAuthState, force);
            switch (loginResult) {
                case SUCCESS:
                    onLoginSuccessful();
                    break;
                case FAILURE_AUTH_LEVEL:
                    onLoginFailed(LoginActivity.LOGIN_FAIL_REASON_AUTH_LEVEL);
                    break;
                case FAILURE_INVALID_TOKEN:
                case FAILURE_NO_SUB:
                    onLoginFailed(LoginStateManager.LoginResult.FAILURE_EXCEPTION.toString());
                    break;
                case FAILURE_WRONG_SUB:
                    onWrongSub(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                            receivedTokenResponse(tokenResponse, authException, true);
                        }
                    });
                    break;
                case FAILURE_EXCEPTION:
                    onLoginFailed(LoginStateManager.LoginResult.FAILURE_EXCEPTION.toString());
                    break;
            }
        } else {
            LogUtil.e(TAG, "receivedTokenResponse: no token");
            onLoginFailed("NO_TOKEN");
        }

    }

    private void onWrongSub(final MaterialDialog.SingleButtonCallback onPositive) {
        LogUtil.d(TAG, "onWrongSub() called with: onPositive = [" + onPositive + "]");

        new MaterialStyledDialog.Builder(this)
                .autoDismiss(false)
                .setTitle(R.string.login_wrong_user_title)
                .setDescription(R.string.login_wrong_user_desc)
                .setHeaderColor(R.color.android_blue)
                .setStyle(Style.HEADER_WITH_ICON)
                .withDialogAnimation(true)
                .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_account, R.color.white, 100))
                .setPositiveText(R.string.dialog_yes_option)
                .setNegativeText(R.string.cancel)
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                        startActivity(new Intent(LoginReceiveActivity.this, StartActivity.class));
                        finish();
                    }
                })
                .onPositive(onPositive)
                .show();
    }


    private void onLoginSuccessful() {
        //cancel "login required" notification if present
        NotificationManagerCompat.from(this).cancel(UpdateMedicationHelper.NOTIFICATION_LOGIN_REQUIRED);

        final Intent i = new Intent(this, HomePagerActivity.class);
        fade(loggingInLayout, 200, false, null);
        fade(loggedInLayout, 1000, true, new Callable() {
            @Override
            public Object call() throws Exception {
                fade(loggedInLayout, 800, false, null);
                startActivity(i);
                finish();
                return null;
            }
        });

    }

    private void call(final Callable c) {
        if (c != null) {
            try {
                c.call();
            } catch (Exception e) {
                LogUtil.e(TAG, "call: ", e);
            }
        }
    }

    @UiThread
    private void fade(final View v, long ms, boolean fadeIn, final Callable c) {
        if (fadeIn) {
            v.setAlpha(0);
            v.setVisibility(View.VISIBLE);
            v.animate().setDuration(ms).alpha(1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    call(c);
                }
            });
        } else {
            v.animate().setDuration(ms).alpha(0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    v.setVisibility(View.GONE);
                    call(c);
                }
            });
        }
    }

    private void onLoginFailed(@Nullable final String failReason) {
        Intent i = new Intent(this, LoginActivity.class);
        i.setAction(LoginActivity.ACTION_LOGIN_FAILED);
        if (failReason != null) {
            i.putExtra(LoginActivity.EXTRA_LOGIN_FAILED_REASON, failReason);
        }
        startActivity(i);
        finish();
    }

    private void  exchangeAuthorizationCode(AuthorizationResponse authorizationResponse) {
        try {
            Map<String, String> additionalParams = InstanceIDHelper.getAdditionalParams(this);
            if (additionalParams != null) {
                performTokenRequest(authorizationResponse.createTokenExchangeRequest(additionalParams));
            } else {
                performTokenRequest(authorizationResponse.createTokenExchangeRequest());
            }
        } catch (InstanceIDHelper.NoInstanceIDException e) {
            LogUtil.e(TAG, "exchangeAuthorizationCode: ", e);
            onLoginFailed("INSTANCE_ID");
        }
    }


    private void performTokenRequest(TokenRequest request) {
        ClientAuthentication clientAuthentication;
        try {
            clientAuthentication = mAuthState.getClientAuthentication();
        } catch (ClientAuthentication.UnsupportedAuthenticationMethod ex) {
            LogUtil.d(TAG, "Token request cannot be made, client authentication for the token "
                    + "endpoint could not be constructed (%s)", ex);
            return;
        }

        mAuthService.performTokenRequest(
                request,
                clientAuthentication,
                new AuthorizationService.TokenResponseCallback() {
                    @Override
                    public void onTokenRequestCompleted(
                            @Nullable TokenResponse tokenResponse,
                            @Nullable AuthorizationException ex) {
                        receivedTokenResponse(tokenResponse, ex, false);
                    }
                });
    }

}
