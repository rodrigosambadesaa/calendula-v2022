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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.TextUtils;

import com.nimbusds.jwt.SignedJWT;

import net.openid.appauth.AuthState;

import org.json.JSONException;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.DefaultDataGenerator;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.StartActivity;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.jobs.UpdateMedicationFromServiceJob;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.security.SecurePrefBundle;

/**
 * Handles login operations and state
 * <p>
 */
public class LoginStateManager {

    private static final String TAG = "LoginStateManager";

    private static LoginStateManager theInstance;

    private SharedPreferences prefs;

    private LoginStateManager() {
        prefs = PreferenceUtils.instance().preferences();
    }

    public static LoginStateManager getInstance() {
        if (theInstance == null) {
            theInstance = new LoginStateManager();
        }
        return theInstance;
    }

    /**
     * Checks if the user is logged in.
     *
     * @return <code>true</code> if the user is logged in, <code>false</code> otherwise
     */
    public boolean isLoggedIn() {
        return getCurrentAuthState() != null;
    }

    /**
     * Logs the user identified by <code>idToken</code> in.
     *
     * @param ctx       the {@link Context}
     * @param authState the {@link AuthState} after a successful OpenID login sequence
     * @param force     if <code>true</code>, will force login even if the user is different, deleting previous data.
     * @return the login result
     */
    public LoginResult login(@NonNull final Context ctx, @NonNull final AuthState authState, final boolean force) {
        LogUtil.d(TAG, "login() called; force = " + force);

        final SignedJWT idToken = verifyIdToken(authState);
        if (idToken == null) {
            LogUtil.e(TAG, "login: invalid or empty id token");
            return LoginResult.FAILURE_INVALID_TOKEN;
        }

        try {
            final String sub = idToken.getJWTClaimsSet().getSubject();
            if (sub == null) {
                LogUtil.e(TAG, "login: token contains no subject!");
                return LoginResult.FAILURE_NO_SUB;
            }

            // check auth level
            final boolean validScope = checkScope(authState.getScope());
            if (!validScope) {
                LogUtil.w(TAG, "login: scope is not valid. Scope: " + authState.getScope());
                return LoginResult.FAILURE_AUTH_LEVEL;
            }


            // check if user is the same as last logged in user
            final String lastUser = PreferenceUtils.getString(PreferenceKeys.LOGIN_LAST_LOGGEDIN_USER, null);
            if (lastUser != null && !lastUser.equals(sub)) {
                if (force) {
                    logoutAndClearData(ctx);
                } else {
                    LogUtil.e(TAG, "login: token subject does not match the existing user");
                    return LoginResult.FAILURE_WRONG_SUB;
                }
            }

            // set active user, create it if not present
            Patient p = DB.patients().findOneBy(Patient.COLUMN_CODE, sub);
            if (p == null) {
                LogUtil.d(TAG, "login: patient does not exist yet; creating local record");
                p = new Patient();
                p.setCode(sub);
                p.setName(ctx.getString(R.string.default_user_name));
                p.setDataRetrieved(false);
                if (DB.patients().getDefault() == null) {
                    p.setDefault(true);
                }
                try {
                    DB.patients().create(p);
                    DefaultDataGenerator.generateDefaultRoutines(p, ctx);
                } catch (SQLException e) {
                    LogUtil.e(TAG, "login: couldn't create patient", e);
                }
            }

            DB.patients().setActive(p);

            // store needed things in prefs
            prefs.edit()
                    .remove(PreferenceKeys.LOGIN_SKIP_LOGIN.key()) //remove skip
                    .putString(PreferenceKeys.LOGIN_ID.key(), sub)
                    .putString(PreferenceKeys.LOGIN_LAST_LOGGEDIN_USER.key(), sub)
                    .apply();

            SecurePrefBundle.INSTANCE
                    .setAuthState(authState.jsonSerializeString())
                    .apply();

            UpdateMedicationFromServiceJob.scheduleUniquePeriodic();

            LogUtil.d(TAG, "login: user logged in successfully");
            return LoginResult.SUCCESS;
        } catch (ParseException e) {
            LogUtil.e(TAG, "login: ", e);
            return LoginResult.FAILURE_EXCEPTION;
        }
    }

    /**
     * Logs the user out.
     *
     * @param defer if <code>true</code>, the tokens will be deleted, but the actual logout will be deferred until the app is opened again
     */
    public void logout(Context ctx, final boolean defer) {

        LogUtil.d(TAG, "logout() called with: defer = [" + defer + "]");
        UpdateMedicationFromServiceJob.cancelAll();
        SecurePrefBundle.INSTANCE
                .clearAuthState()
                .apply();
        final SharedPreferences.Editor editor = prefs.edit();
        if (defer) {
            editor.putBoolean(PreferenceKeys.PATIENTS_REMOVE_ACTIVE_ON_BOOT.key(), true);
            editor.apply();
        } else {
            editor.remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit();
            clearRemoteSession(ctx);
        }
    }

    public void logoutAndClearData(final Context ctx) {
        LogUtil.d(TAG, "logoutAndClearData() called with: ctx = [" + ctx + "]");
        LogoutHelper.clearData(ctx);
    }

    /**
     * Invokes the OpenID provider logout endpoint if exist, for removing session cookies
     *
     * @param ctx
     */
    private void clearRemoteSession(Context ctx) {
        final OpenIdProviderConfiguration openIdProviderConfiguration = OpenIdProviderConfiguration.instance();
        if (openIdProviderConfiguration.requiresLogout()) {
            Intent intent = new Intent(ctx, LogoutActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } else {
            Intent intent = new Intent(ctx, StartActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ctx.startActivity(intent);
        }

    }

    /**
     * Called when the login is skipped (user chooses to continue without logging in)
     */
    public void skipLogin() {
        prefs.edit().putBoolean(PreferenceKeys.LOGIN_SKIP_LOGIN.key(), true).apply();
    }

    /**
     * Checks if the login was skipped.
     *
     * @return <code>true</code> if the login was skipped, <code>false</code> otherwise
     */
    public boolean isLoginSkipped() {
        return prefs.getBoolean(PreferenceKeys.LOGIN_SKIP_LOGIN.key(), false);
    }

    /**
     * Checks if the user is not logged in and the login has not been skipped.
     * If <code>true</code>, this means a login screen should be shown.
     *
     * @return whether a login screen should be shown
     */
    public boolean shouldAskForLogin() {
        return !isLoggedIn() && !isLoginSkipped();
    }

    public AuthState getCurrentAuthState() {
        final String stateString = SecurePrefBundle.INSTANCE.getAuthState();
        if (stateString == null) {
            return null;
        }
        try {
            return AuthState.jsonDeserialize(stateString);
        } catch (JSONException e) {
            LogUtil.e(TAG, "getCurrentAuthState: ", e);
            return null;
        }
    }

    public void updateAuthState(final AuthState newAuthState) {
        SecurePrefBundle.INSTANCE.setAuthState(newAuthState.jsonSerializeString()).apply();
    }

    public String getUserId() {
        return prefs.getString(PreferenceKeys.LOGIN_ID.key(), null);
    }

    /**
     * Checks if the scope contains the required strings (as set in login.scope prop).
     * In practice, this strings may mean the authorization level.
     *
     * @param scope the scope string contained in the OpenID JSON response
     * @return <code>true</code> if no scope check is needed or if the scope matches the constraints,
     * <code>false</code> otherwise.
     */
    private boolean checkScope(@Nullable final String scope) {
        final String requiredScopes = BuildConfig.LOGIN_SCOPES;

        if (TextUtils.isEmpty(requiredScopes)) {
            // no scope required
            return true;
        } else if (TextUtils.isEmpty(scope)) {
            // some scope is required but there's none in the response
            return false;
        }

        // check if all scopes requirements are satisfied
        final List<String> requiredScopeList = Arrays.asList(requiredScopes.split(","));
        final Set<String> actualScopeSet = new HashSet<>(Arrays.asList(scope.split("\\s+")));

        return actualScopeSet.containsAll(requiredScopeList);
    }

    private SignedJWT verifyIdToken(AuthState authState) {
        LogUtil.d(TAG, "receivedTokenResponse: token received");
        //first, parse JWT from id token string
        SignedJWT idToken = null;
        try {
            idToken = SignedJWT.parse(authState.getIdToken());
            //verify the signature...
            if (TokenUtils.verifyToken(idToken)) {
                return idToken;
            } else {
                LogUtil.i(TAG, "getSub: token verification failed!");
            }
            return null;
        } catch (Exception e) {
            LogUtil.e(TAG, "getAndVerifyIdToken: ", e);
            return null;
        }
    }

    public enum LoginResult {
        SUCCESS,
        FAILURE_INVALID_TOKEN,
        FAILURE_AUTH_LEVEL,
        FAILURE_NO_SUB,
        FAILURE_WRONG_SUB,
        FAILURE_EXCEPTION
    }

}
