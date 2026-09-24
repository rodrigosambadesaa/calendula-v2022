package es.usc.citius.servando.calendula.login;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.NetworkUtils;
import es.usc.citius.servando.calendula.util.security.SecurePrefBundle;


public class InstanceIDHelper {

    public static final String PARAM_IID_TOKEN = "iid_token";
    private static final String TAG = "InstanceIDHelper";

    /**
     * Gets Firebase InstanceID token and prepares a map to be used in oauth calls to our proxy
     *
     * @param context a {@link Context}
     * @return a {@link Map} containing only one key/value pair: the instanceID token with {@link #PARAM_IID_TOKEN} as a key
     * @throws NoInstanceIDException if there's no network connection or firebase token fetch fails
     */
    public static Map<String, String> getAdditionalParams(final Context context) throws NoInstanceIDException {

        if (BuildConfig.LOGIN_INSTANCEID_ENABLED) {
            final boolean isNetworkAvailable = NetworkUtils.isNetworkAvailable(context);
            if (!isNetworkAvailable) {
                throw new NoInstanceIDException(NoInstanceIDException.Reason.NO_CONNECTION);
            }
            try {
                // try to recover instance id token from secure prefs
                String iit = SecurePrefBundle.INSTANCE.getInstanceId();
                // call instance id service if we don't have a saved token
                if (iit == null) {
                    iit = getFirebaseToken();
                    if (iit == null) {
                        LogUtil.e(TAG, "getAdditionalParams: instanceID token is null");
                        throw new NoInstanceIDException(NoInstanceIDException.Reason.UNSPECIFIED);
                    } else {
                        // if not null update saved instance id token
                        SecurePrefBundle.INSTANCE.setInstanceId(iit).apply();
                    }
                }
                LogUtil.d(TAG, "InstanceId available");
                Map<String, String> params = new HashMap<>();
                params.put(PARAM_IID_TOKEN, iit);
                return params;
            } catch (Exception e) {
                throw new NoInstanceIDException(NoInstanceIDException.Reason.UNSPECIFIED, e);
            }
        }
        return null;

    }

    private static String getFirebaseToken() {
        final CountDownLatch lock = new CountDownLatch(1);
        final StringBuilder newTokenBuilder = new StringBuilder();
        String newToken = null;
        FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
            @Override
            public void onSuccess(InstanceIdResult instanceIdResult) {
                String token = instanceIdResult.getToken();
                LogUtil.d(TAG, "getInstanceId success");
                newTokenBuilder.append(token);
                lock.countDown();
            }
        }).addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
                LogUtil.d(TAG, "getInstanceId canceled");
                lock.countDown();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                LogUtil.d(TAG, "getInstanceId failed: ",e);
                lock.countDown();
            }
        });

        try {
            lock.await();
            newToken = newTokenBuilder.toString();
            LogUtil.d(TAG, "New InstanceId token retrieved");
        } catch (InterruptedException e) {
            LogUtil.w(TAG, "An error occurred while retrieving InstanceID: ", e);
        }
        return newToken;
    }


    public static class NoInstanceIDException extends Exception {

        private final Reason reason;
        private final Throwable cause;

        private NoInstanceIDException(Reason reason) {
            this.reason = reason;
            this.cause = null;
        }

        private NoInstanceIDException(Reason reason, Throwable cause) {
            this.reason = reason;
            this.cause = cause;
        }

        public Reason getReason() {
            return reason;
        }

        public Throwable getCause() {
            return cause;
        }

        @Override
        public String getMessage() {
            return "Reason: " + reason.toString();
        }

        public enum Reason {
            NO_CONNECTION,
            UNSPECIFIED
        }
    }
}
