package es.usc.citius.servando.calendula.healthcareprovider.remote.extraInfo;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import net.openid.appauth.AuthState;
import net.openid.appauth.TokenResponse;

import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.GetExtraInfoEvent;
import es.usc.citius.servando.calendula.events.GetExtraInfoEvent.Status;
import es.usc.citius.servando.calendula.healthcareprovider.util.DBUtil;
import es.usc.citius.servando.calendula.login.InstanceIDHelper;
import es.usc.citius.servando.calendula.login.LoginActivity;
import es.usc.citius.servando.calendula.login.LoginStateManager;
import es.usc.citius.servando.calendula.login.TestingConnectionBuilder;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;
import es.usc.citius.servando.calendula.util.GsonUtil;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.NetworkPreflightInterceptor;
import es.usc.citius.servando.calendula.util.NetworkUtils;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;
import es.usc.citius.servando.calendula.util.debug.StethoHelper;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import static android.util.Base64.DEFAULT;

public class ExtraInfoHelper {

    public static final int NOTIFICATION_LOGIN_REQUIRED = 5748620;
    private static final String TAG = "ExtraInfoHelper";
    private static final String FILE_NAME = "extrainfo";

    private static ExtraInfoHelper theInstance;
    private ExtraInfoService service;

    private ExtraInfoHelper() {
        service = createExtraInfoService();
    }

    public static ExtraInfoHelper instance() {
        if (theInstance == null)
            theInstance = new ExtraInfoHelper();
        return theInstance;
    }


    /**
     * Fetch Extra info from the remote provider endpoint
     *
     * @return Extra info associated to the patient
     */
    public GetExtraInfoEvent fetchExtraInfo(final Context ctx) {
        Status status;
        String url = null;

        final AuthState authState = LoginStateManager.getInstance().getCurrentAuthState();
        // check patient count: there may be a split second when the user is logged in, the DB is installed, but the Patient object hasn't been created yet
        if (!DBUtil.isValidDB() || authState == null || DB.patients().count() == 0) {
            LogUtil.w(TAG, "onRunJob: no database or no user logged in");
            status = Status.ERROR_NO_USER_OR_DB;
        } else if (!NetworkUtils.isNetworkAvailable(ctx)) {
            status = Status.ERROR_NO_CONNECTION;
        } else if (authState.getAccessToken() == null) {
            // user is logged in but has not an access token
            // force login next time the user opens the app
            status = Status.ERROR_AUTHORIZATION;
        } else {

            notifyStatusUpdate(GetExtraInfoEvent.Status.REQUEST_START, null);

            Map<String, String> params;
            try {
                params = InstanceIDHelper.getAdditionalParams(ctx);
            } catch (InstanceIDHelper.NoInstanceIDException e) {
                LogUtil.e(TAG, "Could not fetch Extra info because there is no InstanceID", e);
                return new GetExtraInfoEvent(Status.ERROR_GENERIC, null);
            }

            String iit = null;
            if (params != null && params.containsKey(InstanceIDHelper.PARAM_IID_TOKEN)) {
                iit = params.get(InstanceIDHelper.PARAM_IID_TOKEN);
                LogUtil.d(TAG, "InstanceId token available");
            }

            try {
                final Response<ProviderResponse> response = service.callExtraInfoService("./", iit).execute();
                final int code = response.code();
                LogUtil.d(TAG, "ExtraInfoService response code is: " + code);
                if (response.isSuccessful()) {
                    final ExtraInfo info = parseExtraInfo(response.body());
                    if (info != null && info.pdf != null && !info.pdf.trim().isEmpty()) {
                        byte[] byteStream = Base64.decode(info.pdf, DEFAULT);
                        try {
                            File tmpFile = File.createTempFile(FILE_NAME, ".pdf", ctx.getCacheDir());
                            tmpFile.deleteOnExit();
                            FileUtils.copyInputStreamToFile(new ByteArrayInputStream(byteStream), tmpFile);
                            status = Status.SUCCESS;
                            url = tmpFile.toString();
                        } catch (IOException e) {
                            LogUtil.e(TAG, "Error retrieving ExtraInfo:", e);
                            status = Status.ERROR_GENERIC;
                        }
                    } else {
                        LogUtil.e(TAG, "Error retrieving ExtraInfo: response contains no PDF");
                        status = Status.ERROR_GENERIC;
                    }
                } else {
                    LogUtil.e(TAG, "Error retrieving ExtraInfo.");
                    switch (code) {
                        case HttpURLConnection.HTTP_UNAUTHORIZED:
                            status = Status.ERROR_AUTHORIZATION;
                            break;
                        default:
                            status = Status.ERROR_GENERIC;
                            break;

                    }
                }
            } catch (IOException e) {
                LogUtil.e(TAG, "fetchExtraInfo: ", e);
                if(NetworkUtils.isNetworkAvailable(ctx))
                    status = GetExtraInfoEvent.Status.ERROR_GENERIC;
                else
                    status = GetExtraInfoEvent.Status.ERROR_NO_CONNECTION;
            } catch (Exception e) {
                // for unexpected stuff
                LogUtil.e(TAG, "fetchExtraInfo: ", e);
                status = GetExtraInfoEvent.Status.ERROR_GENERIC;
            }
        }
        return new GetExtraInfoEvent(status, url);
    }

    static ExtraInfo parseExtraInfo(ProviderResponse response) {
        if (response == null || response.error != null || response.result == null) {
            return null;
        }
        try {
            return GsonUtil.get().fromJson(response.result, ExtraInfo.class);
        } catch (RuntimeException e) {
            LogUtil.e(TAG, "Unable to parse provider extra-info payload", e);
            return null;
        }
    }

    /**
     * Creates a retrofit service that is allows to fetch data from the userInfo endpoint
     *
     * @return The UserInfoService
     */
    private ExtraInfoService createExtraInfoService() {

        final OkHttpClient.Builder clientBuilder = NetworkPreflightInterceptor.installOn(
                new OkHttpClient.Builder(), CalendulaApp.getContext())
                .addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(@NonNull Chain chain) throws IOException {
                        final AuthState state = LoginStateManager.getInstance().getCurrentAuthState();
                        final String currentAccessToken = state != null ? state.getAccessToken() : null;
                        if (currentAccessToken == null) {
                            throw new IOException("Access token is unavailable");
                        }
                        final Request request = chain.request().newBuilder()
                                .addHeader("Authorization", String.format("%s %s", TokenResponse.TOKEN_TYPE_BEARER, currentAccessToken))
                                .build();


                        return chain.proceed(request);
                    }
                });

        if (BuildConfig.DISABLE_HOSTNAME_VERIFICATION) {
            clientBuilder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            clientBuilder.sslSocketFactory(TestingConnectionBuilder.getTrustingContext().getSocketFactory(),
                    TestingConnectionBuilder.getTrustManager());
        }

        clientBuilder.followRedirects(true)
                .followSslRedirects(true);

        if (BuildConfig.DEBUG) {
            new StethoHelper().configureInterceptor(clientBuilder);
        }

        final OkHttpClient httpClient = clientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(httpClient)
                .baseUrl(BuildConfig.OAUTH_EXTRAINFO_ENDPOINT + "/")
                .addConverterFactory(ProviderResponseConverterFactory.create())
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();

        return retrofit.create(ExtraInfoService.class);
    }

    /**
     * Sends events to notify other parts of the app of a failure
     *
     * @param status status to post
     * @param url optional local URL
     */
    public static void notifyStatusUpdate(GetExtraInfoEvent.Status status, String url) {
        CalendulaApp.eventBus().post(new GetExtraInfoEvent(status, url));
    }

    /**
     * Sends a pre-built event to notify other parts of the app.
     *
     * @param event event to post
     */
    public static void notifyStatusUpdate(GetExtraInfoEvent event) {
        if (event != null) {
            CalendulaApp.eventBus().post(event);
        }
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
                        PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT)
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION_LOGIN_REQUIRED, mBuilder.build());
        }
    }
}

