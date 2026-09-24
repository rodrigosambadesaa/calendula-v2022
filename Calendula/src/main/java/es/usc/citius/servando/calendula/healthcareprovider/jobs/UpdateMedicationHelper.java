package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import org.hl7.fhir.dstu3.model.MessageHeader;
import org.hl7.fhir.dstu3.model.OperationOutcome;
import org.joda.time.DateTime;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Random;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.model.persistence.HomogeneousGroup;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.events.ActiveMedUpdateEvent;
import es.usc.citius.servando.calendula.events.ActiveMedUpdateEvent.Status;
import es.usc.citius.servando.calendula.login.LoginActivity;
import es.usc.citius.servando.calendula.login.LoginStateManager;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.healthcareprovider.fhir.ResponseVO;
import es.usc.citius.servando.calendula.healthcareprovider.fhir.FHIRUtil;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.remote.RemoteQueryManager;
import es.usc.citius.servando.calendula.healthcareprovider.util.ActiveMedUpdater;
import es.usc.citius.servando.calendula.healthcareprovider.util.DBUtil;
import es.usc.citius.servando.calendula.util.GsonUtil;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.NetworkUtils;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

public class UpdateMedicationHelper {

    public static final int NOTIFICATION_LOGIN_REQUIRED = 5748620;
    private static final String TAG = "UpdateMedicationHelper";

    private static Random random = new Random();

    @NonNull
    public static Status checkForUpdates(final Context ctx) {

        Status status;
        // check patient count: there may be a split second when the user is logged in, the DB is installed, but the Patient object hasn't been created yet
        if (!DBUtil.isValidDB() || !LoginStateManager.getInstance().isLoggedIn() || DB.patients().count() == 0) {
            LogUtil.w(TAG, "onRunJob: no database or no user logged in");
            return Status.ERROR_NO_USER_OR_DB;
        } else if (!NetworkUtils.isNetworkAvailable(ctx)) {
            return Status.ERROR_NO_CONNECTION;
        } else if (LoginStateManager.getInstance().getCurrentAuthState().getAccessToken() == null) {
            // user is logged in but has not an access token
            // force login next time the user opens the app
            return Status.ERROR_AUTHORIZATION;
        } else {

            notifyStatusUpdate(Status.UPDATE_START);

            final String lastHash = PreferenceUtils.getString(PreferenceKeys.REMOTE_LAST_HASH, null);
            try {
                // do call and parse active meds
                final ResponseVO responseVO = RemoteQueryManager.instance().checkForActiveMeds(lastHash);
                LogUtil.d(TAG, "checkForUpdates: provider response code = " + responseVO.getCode());

                switch (responseVO.getCode()) {
                    case HttpURLConnection.HTTP_OK:

                        // either new info or unchanged

                        if (isNoChanges(responseVO)) {
                            LogUtil.d(TAG, "onRunJob: received unchanged response from server");
                        } else {
                            final String newHash = responseVO.getHeader().getResponse().getIdentifier() + (BuildConfig.DEBUG ? random.nextInt() : "");
                            if (newHash.equals(lastHash)) {
                                LogUtil.w(TAG, "onRunJob: Response indicates new data, but hashes are equal! Skipping addition");
                            } else {
                                LogUtil.d(TAG, "onRunJob: Received new med data!");
                                final List<ActiveMedVO> meds = FHIRUtil.genVOsFromResponse(responseVO);
                                ActiveMedUpdater.INSTANCE.onNewMeds(newHash, meds, ctx, true);
                            }
                        }

                        onSuccess();
                        status = Status.SUCCESS;
                        break;
                    case HttpURLConnection.HTTP_UNAUTHORIZED:
                        // login isn't valid
                        status = onAuthorizationError(responseVO, ctx);
                        break;
                    default:
                        status = Status.ERROR_GENERIC;
                }
            } catch (IOException e) {
                LogUtil.e(TAG, "checkForUpdates: ", e);
                return Status.ERROR_NO_CONNECTION;
            } catch (Exception e) {
                // for unexpected stuff
                LogUtil.e(TAG, "checkForUpdates: ", e);
                return Status.ERROR_GENERIC;
            }

        }
        return status;
    }

    /**
     * Sets preferences and events to notify other parts of the app of a failure
     *
     * @param status
     */
    public static void notifyStatusUpdate(Status status) {
        PreferenceUtils.edit().putString(PreferenceKeys.REMOTE_UPDATE_STATUS.key(), status.toString()).apply();
        CalendulaApp.eventBus().post(new ActiveMedUpdateEvent(status));
    }

    public static Medicine addMedicineToMedKit(ActiveMedEntity entity, es.usc.citius.servando.calendula.persistence.Patient p) {
        Medicine m = null;
        LogUtil.d(TAG, "addMedicineToMedKit: " + entity.getCode() + ", " + entity.getType());
        if (entity.getType().equals(ActiveMedType.DCPF)) {
            m = DB.medicines().findByGroupAndPatient(entity.getCode(), p);
            if (m == null) {
                HomogeneousGroup group = DB.drugDB().homogeneousGroups().findByGroupId(entity.getCode());
                if (group != null) {
                    m = Medicine.fromHomogeneousGroup(group);
                    m.setActiveMedId(entity.getId());
                    m.setPatient(p);
                    DB.medicines().save(m);
                } else {
                    LogUtil.w(TAG, "ActiveMed Group not found on local prescriptions db");
                }
            } else {
                m.setActiveMedId(entity.getId());
                DB.medicines().save(m);
                LogUtil.d(TAG, "ActiveMed is in the medKit");
            }
        } else if (entity.getType().equals(ActiveMedType.NATIONAL_CODE)) {
            m = DB.medicines().findByCnAndPatient(entity.getCode(), p);
            if (m == null) {
                Prescription prescription = DB.drugDB().prescriptions().findByCn(entity.getCode());
                if (prescription != null) {
                    m = Medicine.fromPrescription(prescription);
                    m.setActiveMedId(entity.getId());
                    m.setPatient(p);
                    DB.medicines().save(m);
                } else {
                    LogUtil.w(TAG, "ActiveMed CN not found on local prescriptions db");
                }
            } else {
                m.setActiveMedId(entity.getId());
                DB.medicines().save(m);
                LogUtil.d(TAG, "ActiveMed is in the medKit");
            }
        } else {
            LogUtil.d(TAG, "Med type " + entity.getType());
        }
        return m;
    }

    private static boolean isNoChanges(final ResponseVO vo) {
        final MessageHeader.MessageHeaderResponseComponent response = vo.getHeader().getResponse();
        if (response == null || response.getDetailsTarget() == null || response.getDetailsTarget().getIssueFirstRep() == null)
            return false;
        final OperationOutcome.IssueSeverity severity = response.getDetailsTarget().getIssueFirstRep().getSeverity();
        return severity != null && vo.getCode() == HttpURLConnection.HTTP_OK && severity.equals(OperationOutcome.IssueSeverity.INFORMATION);
    }

    /**
     * Called on authorization error
     *
     * @param responseVO the {@link ResponseVO} received from the service
     */
    private static Status onAuthorizationError(final ResponseVO responseVO, final Context ctx) {
        LogUtil.d(TAG, "onAuthorizationError() called; response code = " + responseVO.getCode());
        Status status = Status.ERROR_AUTHORIZATION;
        if (responseVO.getCode() != HttpURLConnection.HTTP_UNAUTHORIZED) {
            LogUtil.e(TAG, "onAuthorizationError called with wrong code! code: " + responseVO.getCode());
        }
        final MessageHeader.MessageHeaderResponseComponent response = responseVO.getHeader().getResponse();
        if (response != null && response.getDetailsTarget() != null && response.getDetailsTarget().getIssueFirstRep() != null) {
            final OperationOutcome.IssueType code = response.getDetailsTarget().getIssueFirstRep().getCode();
            if (code != null) {
                switch (code) {
                    case FORBIDDEN:
                        // invalid access level
                        status = Status.ERROR_AUTHORIZATION_LEVEL;
                        break;
//                    case LOGIN:
//                    case EXPIRED:
                        //missing or invalid token
//                    default:
                }
            }
        }
        return status;
    }

    public static void showLogoutNotification(final Context ctx) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(ctx, NotificationHelper.CHANNEL_DEFAULT_ID)
                        .setSmallIcon(R.drawable.ic_pill_small)
                        .setContentTitle(ctx.getString(R.string.notification_title_login_required))
                        .setContentText(ctx.getString(R.string.notification_text_login_required));

        Intent resultIntent = new Intent(ctx, LoginActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx);
        stackBuilder.addParentStack(LoginActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(NOTIFICATION_LOGIN_REQUIRED, mBuilder.build());
    }

    /**
     * Called on successful call (unchanged or new info)
     */
    private static void onSuccess() {
        final SharedPreferences.Editor editor = PreferenceUtils.edit();
        editor.putString(PreferenceKeys.REMOTE_UPDATE_STATUS.key(), Status.SUCCESS.toString());
        editor.putString(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE.key(), DateTime.now().toString());
        editor.apply();

        CalendulaApp.eventBus().post(new ActiveMedUpdateEvent(Status.SUCCESS));
    }


}
