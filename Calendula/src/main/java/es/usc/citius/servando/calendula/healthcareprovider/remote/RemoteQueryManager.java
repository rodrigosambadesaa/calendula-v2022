package es.usc.citius.servando.calendula.healthcareprovider.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.openid.appauth.TokenResponse;

import java.io.IOException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.login.LoginStateManager;
import es.usc.citius.servando.calendula.login.TestingConnectionBuilder;
import es.usc.citius.servando.calendula.healthcareprovider.fhir.ResponseVO;
import es.usc.citius.servando.calendula.healthcareprovider.fhir.FHIRUtil;
import es.usc.citius.servando.calendula.util.GsonUtil;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.debug.StethoHelper;
import es.usc.citius.servando.calendula.util.security.certificatePinning.CertificatePinningUtils;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class RemoteQueryManager {

    private static final String TAG = "RemoteQueryManager";

    private static RemoteQueryManager theInstance;


    private ActiveMedRemoteService service;

    private RemoteQueryManager() {

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
                        LogUtil.d(TAG, "Request: " + GsonUtil.get().toJson(request));
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

        if (BuildConfig.ENABLE_CERTIFICATE_PINNING) {
            clientBuilder.certificatePinner(CertificatePinningUtils.createOkHttpPinner());
        }

        clientBuilder.followRedirects(true)
                .followSslRedirects(true);

        if (BuildConfig.DEBUG) {
            new StethoHelper().configureInterceptor(clientBuilder);
            clientBuilder.addInterceptor(new LoggingInterceptor());
        }

        final OkHttpClient httpClient = clientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(httpClient)
                .baseUrl(BuildConfig.REMOTE_DISPENSATION_BASE_URL)
                .addConverterFactory(ProviderFHIRConverterFactory.create())
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();

        service = retrofit.create(ActiveMedRemoteService.class);
    }

    public static RemoteQueryManager instance() {
        if (theInstance == null)
            theInstance = new RemoteQueryManager();
        return theInstance;
    }

    /**
     * Handles getting the ResponseVO from the remote. Does <b>not</b> handle doing it in background,
     * that has to be handled by the caller.
     *
     * @param lastHash the stored hash from the last request
     * @return the ResponseVO
     */
    public ResponseVO checkForActiveMeds(@Nullable String lastHash) throws IOException {
        LogUtil.d(TAG, "checkForActiveMeds() called with: lastHash = [" + lastHash + "]");
        final String url = BuildConfig.REMOTE_DISPENSATION_ENDPOINT;
        final Call<ResponseVO> call = service.getDispensationPlan(url, FHIRUtil.generateQuery(lastHash));

        final Response<ResponseVO> response = call.execute();

        ResponseVO vo = null;

        final int code = response.code();
        LogUtil.d(TAG, "checkForActiveMeds: reponse code is: " + code);

        if (response.isSuccessful()) {
            vo = response.body();
        } else {
            try {
                vo = FHIRUtil.parseResponse(response.errorBody().charStream());
            } catch (Exception e) {
                LogUtil.e(TAG, "Can not parse response error body", e);
                // there is no error body, nothing to do
            }
        }

        if (vo == null) // in case there was a _really_ bad error
            vo = new ResponseVO();
        vo.setCode(code);
        return vo;

    }

}
