package es.usc.citius.servando.calendula.activities;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.http.SslError;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;

import android.util.Base64;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;

import org.greenrobot.eventbus.Subscribe;
import org.joda.time.Duration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.events.GetExtraInfoEvent;
import es.usc.citius.servando.calendula.healthcareprovider.jobs.GetExtraInfoFromServiceJob;
import es.usc.citius.servando.calendula.util.HtmlCacheManager;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;

public class PdfViewActivity extends CalendulaActivity {

    public static final String PARAM_PDFVIEW_REQUEST = "pdfview_param_request";

    private static final String TAG = "PdfViewActivity";

    // reference to the request params
    WebViewRequest request;

    Handler handler;
    MaterialStyledDialog loadingDialog;
    View toolbarShadow;
    int color;

    private WebView webView;
    private String url;
    private Long openedPatientId;
    private int requestedJobId = -1;

    @Override
    protected void onDestroy() {
        if (CalendulaApp.eventBus().isRegistered(this)) {
            CalendulaApp.eventBus().unregister(this);
        }
        super.onDestroy();
    }

    // Method called from the event bus
    @SuppressWarnings("unused")
    @Subscribe
    public void handleExtraInfo(final GetExtraInfoEvent event) {


        if (event == null || event.getStatus() == null || isFinishing() || isDestroyed()) {
            return;
        }
        Patient currentPatient = DB.patients().getActive(this);
        Long activeId = currentPatient == null ? null : currentPatient.getId();
        if (!ProviderDocumentGuard.samePatient(openedPatientId, activeId)) {
            // The patient was removed or switched while a provider request was
            // running. Never keep the old medical document visible.
            handler.post(this::finish);
            return;
        }
        if (!ProviderDocumentGuard.canAcceptResponse(
                openedPatientId, activeId, event.getPatientId(),
                requestedJobId, event.getJobId())) {
            // Global event bus: another viewer's response is not ours.
            return;
        }
        url = event.getUrl();
        LogUtil.d(TAG, "Patient-scoped provider document update received");

        switch (event.getStatus()) {
            case REQUEST_START:
                LogUtil.d(TAG, "handleExtraInfo: Extra info requested.");
                break;
            case ERROR_NO_CONNECTION:
                hideLoading();
                handler.post(new Runnable() {
                    public void run() {
                        showErrorToast(getString(R.string.remote_extrainfo_no_internet));
                        finish();
                    }
                });
                break;
            case ERROR_AUTHORIZATION:
            case ERROR_AUTHORIZATION_LEVEL:
                hideLoading();
                handler.post(new Runnable() {
                    public void run() {
                        showErrorToast(getString(R.string.remote_extrainfo_auth_error));
                        finish();
                    }
                });
                break;
            case ERROR_GENERIC:
                hideLoading();
                handler.post(new Runnable() {
                    public void run() {
                        showErrorToast(getString(R.string.remote_extrainfo_generic_error));
                        finish();
                    }
                });
                break;
            case ERROR_NO_USER_OR_DB:
                LogUtil.e(TAG, "handleExtraInfo: no user or db?");
                hideLoading();
                handler.post(new Runnable() {
                    public void run() {
                        showErrorToast(getString(R.string.remote_extrainfo_generic_error));
                        finish();
                    }
                });
                break;
            case SUCCESS:

                // Provider PDF files can declare arbitrarily large page dimensions.
                // Render within a fixed pixel budget before making a PNG data URL.
                File pdfFile = url == null ? null : new File(url);
                String imageDataUrl = null;
                try {
                    if (!ProviderPdfFilePolicy.isTrustedPdf(getCacheDir(), pdfFile)) {
                        throw new IOException("Downloaded PDF is not a trusted private cache file");
                    }
                    try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                            pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                         PdfRenderer renderer = new PdfRenderer(descriptor)) {
                        if (renderer.getPageCount() < 1) {
                            throw new IOException("Downloaded PDF has no pages");
                        }
                        try (PdfRenderer.Page page = renderer.openPage(0)) {
                            int[] size = PdfBitmapBounds.fit(page.getWidth(), page.getHeight());
                            Bitmap bitmap = Bitmap.createBitmap(
                                    size[0], size[1], Bitmap.Config.ARGB_8888);
                            try {
                                bitmap.eraseColor(Color.WHITE);
                                page.render(bitmap, null, null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                                try (ByteArrayOutputStream png = new ByteArrayOutputStream()) {
                                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)) {
                                        throw new IOException("Could not encode PDF page");
                                    }
                                    imageDataUrl = "data:image/png;base64,"
                                            + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
                                }
                            } finally {
                                bitmap.recycle();
                            }
                        }
                    }
                } catch (IOException | RuntimeException invalidPdf) {
                    // Failure is not a successful document load. Never leave
                    // the spinner indefinitely on missing/corrupt PDF files.
                    LogUtil.w(TAG, "Unable to render a bounded PDF preview");
                    hideLoading();
                    handler.post(() -> {
                        showErrorToast(getString(R.string.remote_extrainfo_generic_error));
                        finish();
                    });
                } finally {
                    // Temporary provider documents should not remain in storage,
                    // including after a failed or malformed download.
                    if (ProviderPdfFilePolicy.isTrustedPdf(getCacheDir(), pdfFile)
                            && !pdfFile.delete()) {
                        LogUtil.w(TAG, "Could not clean up provider PDF temporary file");
                    }
                }

                if (imageDataUrl != null) {
                    final String previewUrl = imageDataUrl;
                    handler.post(() -> {
                        Patient active = DB.patients().getActive(this);
                        if (isFinishing() || isDestroyed()
                                || !ProviderDocumentGuard.samePatient(openedPatientId,
                                        active == null ? null : active.getId())) {
                            finish();
                            return;
                        }
                        webView.setWebViewClient(new PdfViewActivity.CustomWebViewClient(request));
                        webView.loadUrl(previewUrl);
                    });
                }
                break;
        }

    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        handler = new Handler();

        //check for request and URL and finish if not present
        try {
            request = getIntent().getParcelableExtra(PARAM_PDFVIEW_REQUEST);
        } catch (BadParcelableException | IllegalArgumentException invalidRequest) {
            LogUtil.w(TAG, "Corrupted provider PDF view request");
            showErrorToast(null);
            finish();
            return;
        }
        Patient patient = DB.patients().getActive(this);
        if (request == null || patient == null || patient.getId() == null
                || patient.getId() <= 0L) {
            LogUtil.w(TAG, "Provider document request has no valid active patient");
            showErrorToast(null);
            finish();
            return;
        } else {
            openedPatientId = patient.getId();
            webView = (WebView) findViewById(R.id.webView1);
            toolbarShadow = findViewById(R.id.tabs_shadow);

            //setup toolbar and statusbar
            color = patient.getColor();
            String title = request.getTitle();
            setupToolbar(title, color);
            setupStatusBar(color);

            //setup the webView
            setupWebView(request);

            CalendulaApp.eventBus().register(this);
            requestedJobId = GetExtraInfoFromServiceJob.scheduleOneShot(
                    false, openedPatientId);
            if (requestedJobId <= 0) {
                LogUtil.w(TAG, "Could not schedule a patient-scoped provider document request");
                showErrorToast(null);
                finish();
            }
        }

    }


    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        LogUtil.d(TAG, "onActionModeStarted");
        if (toolbar != null) {
            toolbarShadow.setVisibility(View.GONE);
            toolbar.setVisibility(View.GONE);
            setupStatusBar(getResources().getColor(R.color.dark_grey_home));
        }
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        LogUtil.d(TAG, "onActionModeFinished");
        if (toolbar != null) {
            setupStatusBar(color);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    toolbar.setAlpha(0);
                    toolbarShadow.setAlpha(0);
                    toolbar.setVisibility(View.VISIBLE);
                    toolbarShadow.setVisibility(View.VISIBLE);
                    toolbar.animate().alpha(1).start();
                    toolbarShadow.animate().alpha(1).start();
                }
            }, 300);


        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }


    private void setupWebView(final WebViewRequest request) {

        //setup progressDialog
        String loadingMessage = request.getLoadingMessage();
        if (loadingMessage == null) loadingMessage = getString(R.string.message_generic_pleasewait);
        webView.setVisibility(View.INVISIBLE);
        showProgressDialog(loadingMessage);
        //misc webView settings
        //set single column layout
        webView.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        //enable pinch to zoom
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setEnableSmoothTransition(true);

        //enable AppCache if requested
        if (request.getCacheType().equals(WebViewRequest.CacheType.APP_CACHE))
            enableAppCache();

    }

    private void hideLoading() {
        // GetExtraInfoEvent can arrive on the background job's posting thread.
        // The Dialog owns a window and must only be dismissed on Android's UI
        // thread. Keep PDF rendering on the worker instead of switching the
        // entire EventBus subscriber to the main thread.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            if (loadingDialog != null && !isDestroyed()) {
                loadingDialog.dismiss();
            }
        } else {
            runOnUiThread(() -> {
                if (loadingDialog != null && !isDestroyed()) {
                    loadingDialog.dismiss();
                }
            });
        }
    }

    private void showProgressDialog(String loadingMsg) {
        final MaterialStyledDialog.Builder builder = new MaterialStyledDialog.Builder(this)
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_file_document, R.color.white, 100))
                .setHeaderColor(R.color.android_blue)
                .withIconAnimation(false)
                .withDialogAnimation(false)
                .setCancelable(false)
                .setNegativeText(R.string.cancel)
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        webView.stopLoading();
                        loadingDialog.dismiss();
                        finish();
                    }
                });

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        // MaterialDialog accepts an unattached custom view and does not expose its internal
        // container here, so a null inflate root is intentional.
        //noinspection InflateParams
        View customView = inflater.inflate(R.layout.dialog_loading_view, null);
        TextView customText = (TextView) customView.findViewById(R.id.loading_description);
        customText.setText(loadingMsg);

        builder.setCustomView(customView);

        loadingDialog = builder.show();
    }

    private void enableAppCache() {
        webView.getSettings().setDomStorageEnabled(true);
        // Remote/cached content does not require direct access to app/device files or
        // content providers. Keep these capabilities disabled to reduce WebView attack surface.
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
    }

    private void showErrorToast(String error) {
        if (error == null) error = getString(R.string.message_generic_pageloaderror);
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }

    /**
     * Interface that must be implemented in order to access the page html
     * and make changes before it is displayed
     */
    public interface HtmlPostprocessor {
        String process(String html);
    }

    private class CustomWebViewClient extends WebViewClient {

        protected final WebViewRequest request;
        protected final List<String> customCssSheets;
        protected boolean pageLoaded = false;

        public CustomWebViewClient(WebViewRequest request) {
            this.request = request;
            this.customCssSheets = request.getCustomCss();
        }

//        @Override
//        public boolean shouldOverrideUrlLoading(WebView view, String url) {
//            //use webview only for the requested URL or suburls, unless external links are enabled
//            if (!pageLoaded || url.contains(PdfViewActivity.this.url)) {
//                return super.shouldOverrideUrlLoading(view, url);
//            } else {
//                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
//                return true;
//            }
//        }
//
//        @RequiresApi(api = Build.VERSION_CODES.N)
//        @Override
//        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
//            //use webview only for the requested URL or suburls, unless external links are enabled
//            if (request.getUrl().toString().contains(PdfViewActivity.this.url) || request.isRedirect()) {
//                return super.shouldOverrideUrlLoading(view, request);
//            } else {
//                startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
//                return true;
//            }
//        }

        @Override
        public void onPageFinished(WebView view, String url) {

            pageLoaded = true;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    webView.setVisibility(View.VISIBLE);
                    hideLoading();
                }
            }, 200);
            LogUtil.d(TAG, "Finished loading URL: " + url);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            LogUtil.e(TAG, "Received error when trying to load page");
            showErrorToast(request.getConnectionErrorMessage());
            hideLoading();
            finish();
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest r, WebResourceResponse errorResponse) {
            LogUtil.e(TAG, "Received HTTP Error when trying to load page");
            showErrorToast(request.getNotFoundErrorMessage());
            hideLoading();
            finish();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // Overriding WebViewClient disables its default SSL-failure
            // cancellation. Finishing the Activity is NOT equivalent to
            // rejecting a still-pending TLS challenge.
            handler.cancel();
            view.stopLoading();
            LogUtil.e(TAG, "Received SSL Error when trying to load page");
            showErrorToast(request.getConnectionErrorMessage());
            hideLoading();
            finish();
        }


    }

    private class SimpleJSCacheInterface {
        private Context ctx;

        SimpleJSCacheInterface(Context ctx) {
            this.ctx = ctx;
        }

        @JavascriptInterface
        public void writeToCache(String html) {

            final Duration ttl = request.getCacheTTL(); //can  be null
            String processed = null;
            // if there is a postprocessor enabled
            if (request.needsPostprocessing()) {
                // instantiate postprocessor
                try {
                    PdfViewActivity.HtmlPostprocessor processor = (PdfViewActivity.HtmlPostprocessor) Class.forName(request.getPostProcessorClassname()).newInstance();
                    // get processed html
                    processed = processor.process(html);
                    // save it to cache if needed
                    if (request.getCacheType().equals(WebViewRequest.CacheType.DOWNLOAD_CACHE)) {
                        HtmlCacheManager.getInstance().put(url, processed, ttl);
                    }

                } catch (Exception e) {
                    LogUtil.e(TAG, "Error trying to post process content", e);
                }
            }
            // in other case, simply write html content to cache
            else if (request.getCacheType().equals(WebViewRequest.CacheType.DOWNLOAD_CACHE)) {
                HtmlCacheManager.getInstance().put(url, html, ttl);
            }

            final String finalHtml = processed != null ? processed : html;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    webView.setWebViewClient(new PdfViewActivity.CustomWebViewClient(request) {
                                                 @Override
                                                 public void onPageFinished(WebView view, String url) {
                                                     pageLoaded = true;
                                                     handler.postDelayed(new Runnable() {
                                                         @Override
                                                         public void run() {
                                                             webView.setVisibility(View.VISIBLE);
                                                             webView.getSettings().setJavaScriptEnabled(request.isJavaScriptEnabled());
                                                             hideLoading();
                                                         }
                                                     }, 200);
                                                 }
                                             }
                    );
                    webView.loadData(finalHtml, "text/html; charset=UTF-8", null);
                    webView.clearHistory();
                }
            });


            // dismiss the loading dialog

        }
    }
}
