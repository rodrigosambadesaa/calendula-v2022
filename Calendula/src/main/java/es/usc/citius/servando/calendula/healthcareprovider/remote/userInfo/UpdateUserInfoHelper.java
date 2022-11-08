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
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.healthcareprovider.remote.userInfo;

import android.content.Context;
import androidx.annotation.NonNull;

import net.openid.appauth.TokenResponse;

import org.apache.commons.text.WordUtils;

import java.io.IOException;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.login.InstanceIDHelper;
import es.usc.citius.servando.calendula.login.LoginStateManager;
import es.usc.citius.servando.calendula.login.TestingConnectionBuilder;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.GsonUtil;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.debug.StethoHelper;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * Encapsulates operation related with userInfo endpoint and user properties update after login
 */
public class UpdateUserInfoHelper {

    private static final String TAG = "UserInfoQueryManager";

    private static UpdateUserInfoHelper theInstance;
    private UserInfoService service;

    private UpdateUserInfoHelper() {
        service = createUserInfoService();
    }

    public static UpdateUserInfoHelper instance() {
        if (theInstance == null)
            theInstance = new UpdateUserInfoHelper();
        return theInstance;
    }

    /**
     * Fetch user info from the remote userInfo endpoint
     *
     * @return The UserInfo
     * @throws IOException
     */
    public UserInfo fetchUserInfo(final Context ctx) throws IOException, InstanceIDHelper.NoInstanceIDException {
        Map<String, String> params = InstanceIDHelper.getAdditionalParams(ctx);
        String iit = null;
        if (params != null && params.containsKey(InstanceIDHelper.PARAM_IID_TOKEN)) {
            iit = params.get(InstanceIDHelper.PARAM_IID_TOKEN);
            LogUtil.d(TAG, "InstanceId token: " + iit);
        }
        final Response<UserInfo> response = service.getUserInfo("./", iit).execute();
        final int code = response.code();
        LogUtil.d(TAG, "userInfo response code is: " + code);
        UserInfo vo = null;
        if (response.isSuccessful()) {
            vo = response.body();
        } else {
            LogUtil.e(TAG, "Error retrieving userInfo" + GsonUtil.get().toJson(response.errorBody()));
        }
        return vo;
    }

    /**
     * Fetch and update user info if not done before
     *
     * @param ctx A {@link Context} required for getting the active patient
     */
    public void fetchAndUpdateUserInfoIfNeeded(final Context ctx) {
        Patient activePatient = DB.patients().getActive(ctx);
        if (activePatient == null) {
            LogUtil.d(TAG, "Active patient is null, will not fetch user info");
        } else if (!activePatient.isDataRetrieved()) {
            try {
                LogUtil.d(TAG, "retrievePatientInfoIfNeeded: Patient name not set,  calling userInfo service");
                UserInfo userInfo = UpdateUserInfoHelper.instance().fetchUserInfo(ctx);
                LogUtil.d(TAG, "userInfo: " + GsonUtil.get().toJson(userInfo));
                String curatedFirstName = userInfo.getFirstName().trim().replaceAll("\\s+", " ");
                String curatedLastName = userInfo.getLastName().trim().replaceAll("\\s+", " ");
                activePatient.setName(WordUtils.capitalize((curatedLastName + ", " + curatedFirstName).toLowerCase()));
                activePatient.setDataRetrieved(true);
                DB.patients().update(activePatient);
                CalendulaApp.eventBus().post(new PersistenceEvents.UserUpdateEvent(activePatient));
            } catch (Exception e) {
                LogUtil.e(TAG, "retrievePatientInfoIfNeeded: couldn't update patient", e);
            }
        }

    }

    /**
     * Creates a retrofit service that is allows to fetch data from the userInfo endpoint
     *
     * @return The UserInfoService
     */
    private UserInfoService createUserInfoService() {

        final OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder().
                addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(@NonNull Chain chain) throws IOException {
                        final String currentAccessToken = LoginStateManager.getInstance().getCurrentAuthState().getAccessToken();
                        if (currentAccessToken == null) {
                            throw new IllegalStateException("Access token is null!");
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
                .baseUrl(BuildConfig.OAUTH_USERINFO_ENDPOINT + "/")
                .addConverterFactory(new UserInfo.UserInfoConverterFactory())
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();

        return retrofit.create(UserInfoService.class);
    }

}
