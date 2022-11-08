package es.usc.citius.servando.calendula.activities;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.http.SslError;
import android.os.Build;
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
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

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
    // switch to disable JavaScript in API<17
    private boolean isJavaScriptInsecure = false;

    @Override
    protected void onDestroy() {
        CalendulaApp.eventBus().unregister(this);
        super.onDestroy();
    }

    // Method called from the event bus
    @SuppressWarnings("unused")
    @Subscribe
    public void handleExtraInfo(final GetExtraInfoEvent event) {


        url = event.getUrl();
        LogUtil.d(TAG, "GetExtraInfoEvent received. Status: " + event.getStatus().name());

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

                String tmpUrl = null;
                try {
                    File pdfFile = new File(url);
                    ParcelFileDescriptor parcelFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_WRITE);
                    if (parcelFileDescriptor != null) {
                        int page_width = 0;
                        int page_height = 0;
                        Bitmap bitmap = null;
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                            com.shockwave.pdfium.PdfiumCore core = new PdfiumCore(this);
                            PdfDocument doc = core.newDocument(parcelFileDescriptor);
                            final int pageCount = core.getPageCount(doc);
                            core.openPage(doc,0);
                            if (pageCount > 0) {
                                page_width = core.getPageWidthPoint(doc, 0);
                                page_height = core.getPageHeightPoint(doc, 0);
                                bitmap = Bitmap.createBitmap(2 * page_width, 2 * page_height, Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(bitmap);
                                canvas.drawColor(Color.WHITE);
                                core.renderPageBitmap(doc, bitmap, 0, 0, 0, 2 * page_width, 2 * page_height);
                                core.closeDocument(doc);
                            }
                        }
                        else {
                            PdfRenderer renderer = new PdfRenderer(parcelFileDescriptor);
                            final int pageCount = renderer.getPageCount();
                            if (pageCount > 0) {
                                PdfRenderer.Page page = renderer.openPage(0);
                                page_width = page.getWidth();
                                page_height = page.getHeight();
                                bitmap = Bitmap.createBitmap(2*page_width, 2*page_height,
                                        Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(bitmap);
                                canvas.drawColor(Color.WHITE);
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                                page.close();
                            }
                            renderer.close();
                        }
                        if (bitmap != null) {

                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            bitmap.setHasAlpha(true);
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                            byte[] byteArray = byteArrayOutputStream.toByteArray();
                            String imageBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT);
                            tmpUrl = "data:image/png;base64," + imageBase64;
                        }

                        parcelFileDescriptor.close();
                    }
                    pdfFile.delete();
                } catch (IOException e) {
                    LogUtil.e(TAG, "handleExtraInfo: ", e);
                    hideLoading();
                    handler.post(new Runnable() {
                        public void run() {
                            showErrorToast(getString(R.string.remote_extrainfo_generic_error));
                            finish();
                        }
                    });
                }

                if (tmpUrl != null) {
                    final String imageUrl = tmpUrl;
                    handler.post(new Runnable() {
                        public void run() {
                            LogUtil.d(TAG, "Opening URL: " + url);
                            webView.setWebViewClient(new PdfViewActivity.CustomWebViewClient(request));
                                LogUtil.d(TAG, "handleExtraInfo: Loading resource from URL");
                            webView.loadUrl(imageUrl);

                        }
                    });
                }

                break;
        }

    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        handler = new Handler();
        //check api version to see if we can use JavaScript
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            isJavaScriptInsecure = true;
        }

        //check for request and URL and finish if not present
        request = getIntent().getParcelableExtra(PARAM_PDFVIEW_REQUEST);
        if (request == null) {
            LogUtil.e(TAG, "onCreate: No PdfViewRequest provided in intent!");
            showErrorToast(null);
            finish();
        } else {

            webView = (WebView) findViewById(R.id.webView1);
            toolbarShadow = findViewById(R.id.tabs_shadow);

            //setup toolbar and statusbar
            color = DB.patients().getActive(this).getColor();
            String title = request.getTitle();
            setupToolbar(title, color);
            setupStatusBar(color);

            //setup the webView
            setupWebView(request);

            CalendulaApp.eventBus().register(this);
            GetExtraInfoFromServiceJob.scheduleOneShot(false);
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
        if (loadingDialog != null)
            loadingDialog.dismiss();
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
        View customView = inflater.inflate(R.layout.dialog_loading_view, null);
        TextView customText = (TextView) customView.findViewById(R.id.loading_description);
        customText.setText(loadingMsg);

        builder.setCustomView(customView);

        loadingDialog = builder.show();
    }

    private void enableAppCache() {
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAppCachePath(getFilesDir().getPath() + "data/" + getPackageName() + "/cache");
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAppCacheEnabled(true);
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
            this.customCssSheets = isJavaScriptInsecure ? null : request.getCustomCss();
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
