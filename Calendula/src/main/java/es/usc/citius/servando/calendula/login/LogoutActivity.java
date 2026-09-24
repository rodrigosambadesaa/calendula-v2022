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

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import net.openid.appauth.AuthorizationService;
import net.openid.appauth.browser.CustomTabManager;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.StartActivity;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.Snack;

public class LogoutActivity extends AppCompatActivity {

    public static final String ACTION_LOGOUT_FAILED = "Calendula.LogoutActivity.ACTION_LOGOUT_FAILED";
    public static final String EXTRA_LOGOUT_FAILED_REASON = "Calendula.LogoutActivity.EXTRA_LOGOUT_FAILED_REASON";

    private static final String TAG = "LogoutActivity";

    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
    @BindView(R.id.logging_out_layout)
    View loggingInLayout;
    @BindView(R.id.logged_out_layout)
    View loggedInLayout;

    private AuthorizationService mAuthService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String action = getIntent().getAction();


        setContentView(R.layout.activity_logout);
        ButterKnife.bind(this);

        progressBar.getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY);
        mAuthService = AuthorizationServiceHelper.createAuthorizationService(this);

        if (action != null && action.equals(ACTION_LOGOUT_FAILED)) {
            final String failReason = getIntent().getStringExtra(EXTRA_LOGOUT_FAILED_REASON);
            Snack.show(getString(R.string.logout_failed) + " [" + failReason + "]", this);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        final Handler handler = new Handler();
        handler.postDelayed(() -> makeLogoutRequest(),1000L);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAuthService != null) {
            mAuthService.dispose();
        }
    }

    private void makeLogoutRequest() {

        CustomTabManager tabManager = mAuthService.getCustomTabManager();
        CustomTabsClient client = tabManager.getClient();
        CustomTabsSession session = tabManager.createSession(new CustomTabsCallback() {
            @Override
            public void onNavigationEvent(int navigationEvent, Bundle extras) {
                if (navigationEvent == CustomTabsCallback.NAVIGATION_FINISHED) {
                    Intent intent = new Intent(LogoutActivity.this, StartActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }
        });
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder(session)
                .setToolbarColor(ContextCompat.getColor(this, R.color.healthcare_provider_dark))
                .setShowTitle(false)
                .enableUrlBarHiding()
                .build();

        // add NO_HISTORY flag in order to close the browser tab after redirect
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        final OpenIdProviderConfiguration openIdProviderConfiguration = OpenIdProviderConfiguration.instance();
        Uri logoutUri = openIdProviderConfiguration.getLogoutUri()
                .buildUpon()
//                .appendQueryParameter("targetUrl", openIdProviderConfiguration.getLogoutRedirect())
                .build();

        LogUtil.d(TAG, "Closing remote authorization session");
        customTabsIntent.launchUrl(this, logoutUri);

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
    }
}
