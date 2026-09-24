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
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationService;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.ActiveMedUpdateEvent.Status;
import es.usc.citius.servando.calendula.login.AuthorizationServiceHelper;
import es.usc.citius.servando.calendula.login.InstanceIDHelper;
import es.usc.citius.servando.calendula.login.LoginStateManager;
import es.usc.citius.servando.calendula.healthcareprovider.remote.userInfo.UpdateUserInfoHelper;
import es.usc.citius.servando.calendula.healthcareprovider.util.DBUtil;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.NetworkUtils;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

public class UpdateMedicationFromServiceJob extends Job {

    public static final String TAG = "UpdMedicationFromServiceJob";
    private static final Integer PERIOD_HOURS = 12;
    private static final int BACKOFF_MAX_RETRIES = 6;
    private static final int CONNECTION_BACKOFF_INITIAL_MS = 2500; // todo max interval should be 5 minutes!
    private static final int CONNECTION_EXECUTION_WINDOW_MS = 2500;
    private static final int ONESHOT_BACKOFF_INITIAL_MS = 500;
    private static final int ONESHOT_EXECUTION_WINDOW_MS = 2000;
    private static final String EXTRA_SHOULD_RETRY = "EXTRA_SHOULD_RETRY";

    public UpdateMedicationFromServiceJob() {
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

    public static boolean scheduleUniquePeriodic() {
        LogUtil.d(TAG, "scheduleUniquePeriodic() called");
        if (isPeriodicScheduled()) {
            LogUtil.d(TAG, "scheduleUniquePeriodic: periodic job already scheduled. Skipping.");
            return false;
        } else {
            LogUtil.d(TAG, "scheduleUniquePeriodic: periodic not scheduled. Scheduling.");
            final JobRequest.Builder builder = new JobRequest.Builder(TAG)
                    .setPeriodic(Duration.standardHours(PERIOD_HOURS).getMillis())
                    .setExtras(getExtras(true))
                    .setRequiresDeviceIdle(false);

            final int jobId = builder.build().schedule();
            PreferenceUtils.edit().putInt(PreferenceKeys.REMOTE_PERIODIC_JOB_ID.key(), jobId).apply();
            return true;
        }
    }

    @NonNull
    private static PersistableBundleCompat getExtras(boolean shouldRetry) {
        PersistableBundleCompat extras = new PersistableBundleCompat();
        extras.putBoolean(EXTRA_SHOULD_RETRY, shouldRetry);
        return extras;
    }

    private static boolean isPeriodicScheduled() {
        final int periodicJobId = PreferenceUtils.getInt(PreferenceKeys.REMOTE_PERIODIC_JOB_ID, -1);
        if (periodicJobId != -1) {
            // the periodic job doesn't get rescheduled so it's fine to use always the same ID
            final JobManager jobManager = JobManager.instance();
            final JobRequest jobRequest = jobManager.getJobRequest(periodicJobId);
            if (jobRequest != null && jobRequest.isPeriodic() && jobRequest.getTag().equals(TAG)) { // some checks just in case
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {


        LogUtil.d(TAG, "onRunJob() called, id is: " + params.getId() + "]");

        LogUtil.d(TAG, "onRunJob: cancelling all other jobs, except this one and the periodic one");
        cancelAllExceptPeriodic(params.getId());

        LogUtil.d(TAG, "onRunJob: Job started");
        Result result = null;


        Status status;

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
                status = UpdateMedicationHelper.checkForUpdates(getContext());
            }
        }
        switch (status) {
            case UPDATE_START:
                // wat
                LogUtil.e(TAG, "onRunJob: invalid status:  " + status.toString());
                result = Result.FAILURE;
                break;
            case SUCCESS:
                LogUtil.d(TAG, "onRunJob: check for update successful");
                result = Result.SUCCESS;
                break;
            case ERROR_NO_CONNECTION:
                LogUtil.w(TAG, "onRunJob: no connection, rescheduling");
                result = Result.FAILURE;
                if (params.getExtras().getBoolean(EXTRA_SHOULD_RETRY, false))
                    scheduleWhenConnected();
                break;
            case ERROR_AUTHORIZATION:
            case ERROR_AUTHORIZATION_LEVEL:
                LogUtil.e(TAG, "onRunJob: Authorization error");
                LoginStateManager.getInstance().logout(getContext(), true);
                UpdateMedicationHelper.showLogoutNotification(getContext());
                result = Result.FAILURE;
                break;
            case ERROR_GENERIC:
                LogUtil.e(TAG, "onRunJob: Generic error");
                result = rescheduleOnError(params);
                break;
            case ERROR_NO_USER_OR_DB:
                LogUtil.w(TAG, "onRunJob: no user or db!");
                UpdateMedicationHelper.showLogoutNotification(getContext());
                result = Result.FAILURE;
                break;
        }

        if (result!=Result.RESCHEDULE) {
            UpdateMedicationHelper.notifyStatusUpdate(status);
        }
        LogUtil.d(TAG, "onRunJob() returned: " + result);
        return result;
    }

    private class RefreshTokensResult {
        private Status result;

        public RefreshTokensResult() {
            this.result = Status.SUCCESS;
        }

        public Status getResult() {
            return result;
        }

        public void setResult(Status result) {
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
                        LogUtil.e(TAG, "refreshTokensIfNeeded: refresh failed. Exception: ", ex);
                        if(ex.code == AuthorizationException.GeneralErrors.NETWORK_ERROR.code)
                            result.setResult(Status.ERROR_NO_CONNECTION);
                        else result.setResult(Status.ERROR_AUTHORIZATION);
                    } else {
                        LoginStateManager.getInstance().updateAuthState(authState);
                        LogUtil.d(TAG, "Access token refreshed successfully");
                        LogUtil.d(TAG, "AccessTokenExpirationTime: " + new DateTime(authState.getAccessTokenExpirationTime()).toString());
                    }
                    countDownLatch.countDown();
                }
            });

            try {
                authService.dispose();
            }
            catch (IllegalArgumentException ex) {
                LogUtil.e(TAG, "refreshTokensIfNeeded: failed to dispose authService: ", ex);
            }

            try{
                LogUtil.d(TAG, "refreshToken: Blocking JOB.");
                countDownLatch.await();
            } catch (InterruptedException e) {
                LogUtil.w(TAG, "onRunJob: ", e);
                result.setResult(Status.ERROR_GENERIC);
            }
            LogUtil.d(TAG, "refreshToken: JOB unblocked.");

        } else if (authState != null) {
            LogUtil.d(TAG, "AccessTokenExpirationTime: " + new DateTime(authState.getAccessTokenExpirationTime()).toString());
        } else {
            LogUtil.d(TAG, "AuthState is null.");
            result.setResult(Status.ERROR_AUTHORIZATION);
        }
        return result.getResult();
    }


    private void cancelAllExceptPeriodic(final int thisId) {
        final JobManager jobManager = JobManager.instance();
        final Set<JobRequest> allJobRequests = jobManager.getAllJobRequests();

        for (JobRequest jobRequest : allJobRequests) {
            final int jobId = jobRequest.getJobId();
            if (!jobRequest.isPeriodic() && jobId != thisId) {
                jobManager.cancel(jobId);
            }
        }
    }

    public static void cancelAll() {
        final JobManager jobManager = JobManager.instance();
        final Set<JobRequest> allJobRequests = jobManager.getAllJobRequests();

        for (JobRequest jobRequest : allJobRequests) {
            final int jobId = jobRequest.getJobId();
            jobManager.cancel(jobId);

        }

        PreferenceUtils.edit().remove(PreferenceKeys.REMOTE_PERIODIC_JOB_ID.key()).apply();
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
