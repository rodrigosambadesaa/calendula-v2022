package es.usc.citius.servando.calendula.activities;

import android.content.Context;
import android.graphics.Color;
import android.net.http.SslError;
import android.view.Gravity;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;

import android.view.ActionMode;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.view.View;
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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.events.GetExtraInfoEvent;
import es.usc.citius.servando.calendula.healthcareprovider.jobs.GetExtraInfoFromServiceJob;
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
    private String openedPatientCode;
    private int requestedJobId = -1;

    // PdfRenderer is not thread-safe. Open, page and close only on this worker.
    // The temporary PDF is unlinked immediately after the renderer opens it.
    private final ExecutorService pdfWorker = Executors.newSingleThreadExecutor();
    private final AtomicInteger previewGeneration = new AtomicInteger();
    private final AtomicBoolean openingPdf = new AtomicBoolean();
    private volatile boolean previewClosed;
    private PrivatePdfPageSession pdfSession; // Access exclusively on pdfWorker.
    private int displayedPdfPage = -1;
    private int totalPdfPages;
    private LinearLayout pdfNavigation;
    private Button previousPdfPage;
    private Button nextPdfPage;
    private TextView pdfPageIndicator;

    /** Do not trust a reused numeric patient ID after SQLite restoration. */
    private boolean sameOpeningPatient() {
        try {
            Patient active = DB.patients().getActive(this);
            return ProviderDocumentGuard.samePatient(openedPatientId, openedPatientCode,
                    active == null ? null : active.getId(),
                    active == null ? null : active.getCode());
        } catch (RuntimeException unavailableDatabase) {
            return false;
        }
    }

    /** Only called on the main thread: hide any private preview immediately. */
    private boolean rejectChangedPatient() {
        if (sameOpeningPatient()) {
            return false;
        }
        previewGeneration.incrementAndGet();
        if (webView != null) {
            webView.setVisibility(View.INVISIBLE);
            webView.stopLoading();
        }
        if (pdfNavigation != null) {
            pdfNavigation.setVisibility(View.INVISIBLE);
        }
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (openedPatientId != null) {
            rejectChangedPatient();
        }
    }

    @Override
    protected void onDestroy() {
        previewClosed = true;
        previewGeneration.incrementAndGet();
        // Queue cleanup after any in-flight page; never close PdfRenderer
        // concurrently with its render call. The original PDF was unlinked
        // on successful open, so process death cannot strand that file.
        pdfWorker.execute(() -> {
            if (pdfSession != null) {
                pdfSession.close();
                pdfSession = null;
            }
        });
        pdfWorker.shutdown();
        // Stop receiving asynchronous provider responses before removing the
        // patient-specific view and pending UI callbacks.
        if (CalendulaApp.eventBus().isRegistered(this)) {
            CalendulaApp.eventBus().unregister(this);
        }
        if (webView != null) {
            // Detach callbacks *before* stopLoading: a synchronous finish
            // callback could otherwise enqueue stale patient UI work.
            webView.setWebViewClient(null);
            webView.stopLoading();
            if (webView.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
            }
            // Destroy the WebView explicitly; retaining its renderer after
            // logout/profile changes can retain a sensitive PDF preview.
            webView.destroy();
            webView = null;
        }
        // Clear after stopping/destroying the renderer so any callbacks
        // enqueued by shutdown cannot outlive this activity.
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (loadingDialog != null) {
            loadingDialog.dismiss();
            loadingDialog = null;
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
        Patient currentPatient;
        try {
            currentPatient = DB.patients().getActive(this);
        } catch (RuntimeException unavailableDatabase) {
            currentPatient = null;
        }
        Long activeId = currentPatient == null ? null : currentPatient.getId();
        String activeCode = currentPatient == null ? null : currentPatient.getCode();
        if (!ProviderDocumentGuard.samePatient(openedPatientId, openedPatientCode,
                activeId, activeCode)) {
            // The patient was removed or switched while a provider request was
            // running. Never keep the old medical document visible.
            handler.post(this::rejectChangedPatient);
            return;
        }
        if (!ProviderDocumentGuard.canAcceptResponse(
                openedPatientId, openedPatientCode, activeId, activeCode,
                event.getPatientId(), requestedJobId, event.getJobId())) {
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
                // Never decode an entire medical PDF or preload later pages.
                // A bounded, serialized worker renders only the requested page.
                openPdfPreview(url == null ? null : new File(url));
                break;
        }

    }

    private void cleanTemporaryPdf(File source) {
        if (ProviderPdfFilePolicy.isTrustedPdf(getCacheDir(), source)
                && !source.delete()) {
            LogUtil.w(TAG, "Unable to delete a private temporary PDF");
        }
    }

    private void openPdfPreview(File source) {
        // Two SUCCESS notifications for the same global provider job must
        // not invalidate the first page's generation token or erase the PDF
        // while the first open is still in progress.
        if (!openingPdf.compareAndSet(false, true)) {
            return;
        }
        final int generation = previewGeneration.incrementAndGet();
        try {
            pdfWorker.execute(() -> {
                if (previewClosed || !sameOpeningPatient()) {
                    cleanTemporaryPdf(source);
                    if (!previewClosed) {
                        handler.post(this::rejectChangedPatient);
                    }
                    return;
                }
                try {
                    pdfSession = PrivatePdfPageSession.open(getCacheDir(), source);
                    renderPdfPageOnWorker(0, generation);
                } catch (PrivatePdfPageSession.UnsupportedLegacyMultipagePdfException legacy) {
                    cleanTemporaryPdf(source);
                    reportPdfFailure(generation, R.string.pdf_legacy_multipage_unavailable);
                } catch (IOException | RuntimeException invalidPdf) {
                    cleanTemporaryPdf(source);
                    reportPdfFailure(generation);
                }
            });
        } catch (RejectedExecutionException closed) {
            cleanTemporaryPdf(source);
        }
    }

    private void reportPdfFailure(int generation) {
        reportPdfFailure(generation, R.string.remote_extrainfo_generic_error);
    }

    private void reportPdfFailure(int generation, int messageResourceId) {
        // No URL or patient document contents may enter Logcat.
        LogUtil.w(TAG, "Unable to render bounded private provider PDF page");
        handler.post(() -> {
            if (previewClosed || generation != previewGeneration.get()
                    || isFinishing() || isDestroyed()) {
                return;
            }
            if (rejectChangedPatient()) {
                return;
            }
            hideLoading();
            showErrorToast(getString(messageResourceId));
            finish();
        });
    }

    private void renderPdfPageOnWorker(int index, int generation) {
        if (previewClosed || generation != previewGeneration.get()) {
            return;
        }
        if (!sameOpeningPatient()) {
            handler.post(() -> {
                if (!previewClosed) {
                    rejectChangedPatient();
                }
            });
            return;
        }
        try {
            if (pdfSession == null) {
                throw new IOException("No private PDF renderer is available");
            }
            PrivatePdfPageSession.RenderedPage image = pdfSession.render(index);
            handler.post(() -> {
                if (previewClosed || generation != previewGeneration.get()
                        || isFinishing() || isDestroyed() || webView == null
                        || rejectChangedPatient()) {
                    return;
                }
                displayedPdfPage = image.index;
                totalPdfPages = image.total;
                ensurePdfNavigation();
                updatePdfNavigation();
                webView.setWebViewClient(new CustomWebViewClient(request, generation));
                webView.loadUrl(image.imageUrl);
            });
        } catch (IOException | RuntimeException invalidPage) {
            reportPdfFailure(generation);
        }
    }

    private void requestPdfPage(int index) {
        if (previewClosed || isFinishing() || isDestroyed()
                || index < 0 || index >= totalPdfPages || rejectChangedPatient()) {
            return;
        }
        int generation = previewGeneration.incrementAndGet();
        if (previousPdfPage != null) {
            previousPdfPage.setEnabled(false);
        }
        if (nextPdfPage != null) {
            nextPdfPage.setEnabled(false);
        }
        try {
            pdfWorker.execute(() -> renderPdfPageOnWorker(index, generation));
        } catch (RejectedExecutionException closed) {
            reportPdfFailure(generation);
        }
    }

    /** Add navigation only to the PDF viewer, preserving the shared HTML layout. */
    private void ensurePdfNavigation() {
        if (pdfNavigation != null) {
            return;
        }
        RelativeLayout root = (RelativeLayout) webView.getParent();
        pdfNavigation = new LinearLayout(this);
        pdfNavigation.setId(View.generateViewId());
        pdfNavigation.setOrientation(LinearLayout.HORIZONTAL);
        pdfNavigation.setGravity(Gravity.CENTER_VERTICAL);
        pdfNavigation.setBackgroundColor(Color.WHITE);

        previousPdfPage = new Button(this);
        previousPdfPage.setText(R.string.pdf_previous_page);
        previousPdfPage.setOnClickListener(v -> requestPdfPage(displayedPdfPage - 1));
        pdfNavigation.addView(previousPdfPage,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        pdfPageIndicator = new TextView(this);
        pdfPageIndicator.setGravity(Gravity.CENTER);
        pdfPageIndicator.setTextColor(Color.BLACK);
        pdfPageIndicator.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        pdfNavigation.addView(pdfPageIndicator,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        nextPdfPage = new Button(this);
        nextPdfPage.setText(R.string.pdf_next_page);
        nextPdfPage.setOnClickListener(v -> requestPdfPage(displayedPdfPage + 1));
        pdfNavigation.addView(nextPdfPage,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        RelativeLayout.LayoutParams footer = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        footer.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        root.addView(pdfNavigation, footer);
        RelativeLayout.LayoutParams preview = (RelativeLayout.LayoutParams) webView.getLayoutParams();
        preview.addRule(RelativeLayout.ABOVE, pdfNavigation.getId());
        webView.setLayoutParams(preview);
    }

    private void updatePdfNavigation() {
        pdfPageIndicator.setText(getString(R.string.pdf_page_indicator,
                displayedPdfPage + 1, totalPdfPages));
        previousPdfPage.setEnabled(displayedPdfPage > 0);
        nextPdfPage.setEnabled(displayedPdfPage < totalPdfPages - 1);
    }

    @Override
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
                || patient.getId() <= 0L || patient.getCode() == null
                || patient.getCode().trim().isEmpty()) {
            LogUtil.w(TAG, "Provider document request has no valid active patient");
            showErrorToast(null);
            finish();
            return;
        } else {
            openedPatientId = patient.getId();
            openedPatientCode = patient.getCode();
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
        // Restrict local device access before enabling JavaScript or loading any document.
        WebViewAccessPolicy.harden(webView.getSettings());
        // Rendered provider PDFs are private data: the preview needs no
        // scripts, remote subresources or browser storage, regardless of the
        // original request's cache flags.
        webView.getSettings().setJavaScriptEnabled(false);
        webView.getSettings().setDomStorageEnabled(false);
        webView.getSettings().setBlockNetworkLoads(true);

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

        // PDF pages are local, bounded PNG data URLs; do not enable DOM cache.

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
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
    }

    private void showErrorToast(String error) {
        if (error == null) error = getString(R.string.message_generic_pageloaderror);
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }

    private class CustomWebViewClient extends WebViewClient {

        protected final WebViewRequest request;
        protected final List<String> customCssSheets;
        protected boolean pageLoaded = false;
        private final int generation;

        public CustomWebViewClient(WebViewRequest request, int generation) {
            this.request = request;
            this.generation = generation;
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
            if (previewClosed || generation != previewGeneration.get()) {
                return;
            }
            pageLoaded = true;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (previewClosed || generation != previewGeneration.get()
                            || isFinishing() || isDestroyed() || webView == null
                            || rejectChangedPatient()) {
                        return;
                    }
                    webView.clearHistory(); // Do not retain earlier clinical pages.
                    webView.setVisibility(View.VISIBLE);
                    hideLoading();
                }
            }, 200);
            // A local PDF preview URL can contain the patient's rendered
            // document as an encoded data URI. Do not put it in Logcat.
            LogUtil.d(TAG, "Provider document preview loaded");
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (previewClosed || generation != previewGeneration.get()) return;
            LogUtil.e(TAG, "Received error when trying to load page");
            showErrorToast(request.getConnectionErrorMessage());
            hideLoading();
            finish();
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest r, WebResourceResponse errorResponse) {
            if (previewClosed || generation != previewGeneration.get()) return;
            LogUtil.e(TAG, "Received HTTP Error when trying to load page");
            showErrorToast(request.getNotFoundErrorMessage());
            hideLoading();
            finish();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            if (previewClosed || generation != previewGeneration.get()) return;
            // Overriding WebViewClient disables its default SSL-failure
            // cancellation. Finishing the Activity is NOT equivalent to
            // rejecting a still-pending TLS challenge.
            view.stopLoading();
            LogUtil.e(TAG, "Received SSL Error when trying to load page");
            showErrorToast(request.getConnectionErrorMessage());
            hideLoading();
            finish();
        }


    }

    // Intentionally no JavaScript bridge: PDF previews are bounded PNG data URLs.

}
