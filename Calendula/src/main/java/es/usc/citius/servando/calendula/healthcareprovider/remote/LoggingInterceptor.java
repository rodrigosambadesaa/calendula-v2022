package es.usc.citius.servando.calendula.healthcareprovider.remote;

import java.io.IOException;

import es.usc.citius.servando.calendula.util.LogUtil;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Debug network logger that intentionally avoids headers and payload bodies.
 *
 * Authentication headers, FHIR payloads and provider responses may contain
 * credentials or personal/health information and must not be written to Logcat.
 */
public class LoggingInterceptor implements Interceptor {

    private static final String TAG = "LoggingInterceptor";

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();

        long started = System.nanoTime();
        LogUtil.d(TAG, "--> " + request.method() + " " + NetworkLogSanitizer.origin(request.url()));

        Response response = chain.proceed(request);

        long elapsedNanos = System.nanoTime() - started;
        LogUtil.d(
                TAG,
                String.format(
                        "<-- %d %s %s (%.1fms)",
                        response.code(),
                        request.method(),
                        NetworkLogSanitizer.origin(request.url()),
                        elapsedNanos / 1e6d));

        return response;
    }
}
