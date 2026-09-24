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
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationService;

import org.joda.time.DateTime;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.GetExtraInfoEvent;
import es.usc.citius.servando.calendula.events.GetExtraInfoEvent.Status;
import es.usc.citius.servando.calendula.login.AuthorizationServiceHelper;
import es.usc.citius.servando.calendula.login.InstanceIDHelper;
import es.usc.citius.servando.calendula.login.LoginStateManager;
import es.usc.citius.servando.calendula.healthcareprovider.remote.extraInfo.ExtraInfoHelper;
import es.usc.citius.servando.calendula.healthcareprovider.remote.userInfo.UpdateUserInfoHelper;
import es.usc.citius.servando.calendula.healthcareprovider.util.DBUtil;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.NetworkUtils;

public class GetExtraInfoFromServiceJob extends Job {

    public static final String TAG = "GetExtraInfoFromServiceJob";
    private static final Integer PERIOD_HOURS = 12;
    private static final int BACKOFF_MAX_RETRIES = 6;
    private static final int CONNECTION_BACKOFF_INITIAL_MS = 2500; // todo max interval should be 5 minutes!
    private static final int CONNECTION_EXECUTION_WINDOW_MS = 2500;
    private static final int ONESHOT_BACKOFF_INITIAL_MS = 500;
    private static final int ONESHOT_EXECUTION_WINDOW_MS = 2000;
    private static final String EXTRA_SHOULD_RETRY = "EXTRA_SHOULD_RETRY";

    public GetExtraInfoFromServiceJob() {
    }

    public static void scheduleOneShot(final boolean shouldRetry) {
        LogUtil.d(TAG, "scheduleOneShot() called with: shouldRetry = [" + shouldRetry + "]");


        final JobRequest.Builder builder = new JobRequest.Builder(TAG)
                .setExecutionWindow(1, ONESHOT_EXECUTION_WINDOW_MS)
                .setExtras(getExtras(shouldRetry))
                .setBackoffCriteria(ONESHOT_BACKOFF_INITIAL_MS, JobRequest.BackoffPolicy.EXPONENTIAL);
        builder.build().schedule();
    }

    public static void scheduleWhenConnected() {
        LogUtil.d(TAG, "scheduleWhenConnected() called");
        final JobRequest.Builder builder = new JobRequest.Builder(TAG)
                .setExecutionWindow(1, CONNECTION_EXECUTION_WINDOW_MS)
                .setBackoffCriteria(CONNECTION_BACKOFF_INITIAL_MS, JobRequest.BackoffPolicy.LINEAR) //wait linearly for connection!
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .setRequirementsEnforced(true);

        builder.build().schedule();
    }

    @NonNull
    private static PersistableBundleCompat getExtras(boolean shouldRetry) {
        PersistableBundleCompat extras = new PersistableBundleCompat();
        extras.putBoolean(EXTRA_SHOULD_RETRY, shouldRetry);
        return extras;
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {


        LogUtil.d(TAG, "onRunJob() called, id is: " + params.getId() + "]");

        LogUtil.d(TAG, "onRunJob: Job started");
        Result result = null;


        Status status;
        String url = null;

        if (!DBUtil.isValidDB() || !LoginStateManager.getInstance().isLoggedIn() || DB.patients().count() == 0) {
            LogUtil.w(TAG, "onRunJob: no database or no user logged in");
            status = Status.ERROR_NO_USER_OR_DB;
        } else if (!NetworkUtils.isNetworkAvailable(getContext())) {
            status = Status.ERROR_NO_CONNECTION;
        } else if (LoginStateManager.getInstance().getCurrentAuthState().getAccessToken() == null) {
            // user is logged in but has not an access token
            // force login next time the user opens the app
            status = Status.ERROR_AUTHORIZATION;
        } else {
            status = refreshTokensIfNeeded();
            if (status == Status.SUCCESS) {
                UpdateUserInfoHelper.instance().fetchAndUpdateUserInfoIfNeeded(getContext());
                final GetExtraInfoEvent event = ExtraInfoHelper.instance().fetchExtraInfo(getContext());
                status = event.getStatus();
                url = event.getUrl();
            }
        }
        switch (status) {
            case REQUEST_START:
                LogUtil.e(TAG, "onRunJob: invalid status:  " + status.toString());
                result = Result.FAILURE;
                break;
            case SUCCESS:
                LogUtil.d(TAG, "onRunJob: request for info successful");
                result = Result.SUCCESS;
                break;
            case ERROR_NO_CONNECTION:
                LogUtil.w(TAG, "onRunJob: no connection, request failed.");
                result = Result.FAILURE;
                if (params.getExtras().getBoolean(EXTRA_SHOULD_RETRY, false))
                    scheduleWhenConnected();
                break;
            case ERROR_AUTHORIZATION:
            case ERROR_AUTHORIZATION_LEVEL:
                LogUtil.e(TAG, "onRunJob: Authorization error");
                LoginStateManager.getInstance().logout(getContext(), true);
                ExtraInfoHelper.showLogoutNotification(getContext());
                result = Result.FAILURE;
                break;
            case ERROR_GENERIC:
                LogUtil.e(TAG, "onRunJob: Generic error");
                result = rescheduleOnError(params);
                break;
            case ERROR_NO_USER_OR_DB:
                LogUtil.w(TAG, "onRunJob: no user or db!");
                ExtraInfoHelper.showLogoutNotification(getContext());
                result = Result.FAILURE;
                break;
        }

        if (result!=Result.RESCHEDULE) {
            ExtraInfoHelper.notifyStatusUpdate(status, url);
        }
        LogUtil.d(TAG, "onRunJob() returned: " + result);
        return result;
    }

    private class RefreshTokensResult {
        private GetExtraInfoEvent.Status result;

        public RefreshTokensResult() {
            this.result = Status.SUCCESS;
        }

        public GetExtraInfoEvent.Status getResult() {
            return result;
        }

        public void setResult(GetExtraInfoEvent.Status result) {
            this.result = result;
        }
    }

    private Status refreshTokensIfNeeded() {

        final RefreshTokensResult result = new RefreshTokensResult();
        Map<String, String> additionalParams;
        try {
            // get instanceId and build additional params map
            additionalParams = InstanceIDHelper.getAdditionalParams(getContext());
        } catch (InstanceIDHelper.NoInstanceIDException e) {
            LogUtil.e(TAG, "Could not update refresh token because there is no InstanceID", e);
            return Status.ERROR_NO_CONNECTION;
        }

        final AuthState authState = LoginStateManager.getInstance().getCurrentAuthState();

        if (authState != null) {
            LogUtil.d(TAG, "refreshTokensIfNeeded: authentication state available");
        }
        if (authState != null && authState.getNeedsTokenRefresh()) {
            LogUtil.d(TAG, "refreshTokensIfNeeded: refreshing tokens");
            LogUtil.d(TAG, "AccessTokenExpirationTime: " + new DateTime(authState.getAccessTokenExpirationTime()).toString());
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final AuthorizationService authService = AuthorizationServiceHelper.createAuthorizationService(getContext());
            authState.performActionWithFreshTokens(authService, additionalParams, new AuthState.AuthStateAction() {
                @Override
                public void execute(@Nullable String accessToken, @Nullable String idToken, @Nullable AuthorizationException ex) {
                    if (ex != null) {
                        LogUtil.d(TAG, "refreshTokensIfNeeded: refresh failed. Exception: ", ex);
                        if (ex.code == AuthorizationException.GeneralErrors.NETWORK_ERROR.code)
                            result.setResult(Status.ERROR_NO_CONNECTION);
                        else result.setResult(Status.ERROR_AUTHORIZATION);
                    } else {
                        LoginStateManager.getInstance().updateAuthState(authState);
                    }
                    countDownLatch.countDown();
                }
            });

            authService.dispose();
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                LogUtil.w(TAG, "onRunJob: ", e);
                result.setResult(Status.ERROR_GENERIC);
            }
        } else if (authState != null) {
            LogUtil.d(TAG, "AccessTokenExpirationTime: " + new DateTime(authState.getAccessTokenExpirationTime()).toString());
        } else {
            LogUtil.d(TAG, "AuthState is null.");
            result.setResult(Status.ERROR_AUTHORIZATION);
        }
        return result.getResult();
    }


    /**
     * Called when the call fails with a status other than 401 unauthorized.
     * Reschedules until max retries.
     *
     * @param params the job params
     * @return the result that the calling job must return
     */
    private Result rescheduleOnError(Params params) {
        LogUtil.w(TAG, "rescheduleOnError: Job failed. Retrying.");

        final boolean shouldRetry = params.getExtras().getBoolean(EXTRA_SHOULD_RETRY, false);

        if (params.isPeriodic() && shouldRetry) {
            //if job is periodic, Result.RESCHEDULE won't work, so run a non-periodic copy
            LogUtil.d(TAG, "rescheduleOnError: job is periodic. Executing oneshot job as a retry.");
            scheduleOneShot(true);
            return Result.FAILURE;
        } else {
            final int failureCount = params.getFailureCount();
            LogUtil.d(TAG, "rescheduleOnError: job is oneshot. failure count= " + failureCount + " backoff policy =" + params.getBackoffPolicy());
            if (failureCount < BACKOFF_MAX_RETRIES && shouldRetry) {
                return Result.RESCHEDULE;
            } else {
                LogUtil.i(TAG, "rescheduleOnError: Max retries reached. Will not retry any further");
                return Result.FAILURE;
            }
        }
    }


}
