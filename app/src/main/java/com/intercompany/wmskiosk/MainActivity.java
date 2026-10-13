package com.intercompany.wmskiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


/**
 * Full-screen WebView that shows the WMS site.
 *
 * Hidden admin gesture: tap the top-right corner of the screen 5 times within 3 seconds,
 * then enter the admin PIN to open {@link SettingsActivity} (URL, full screen, startup,
 * Home-screen mode, close app).
 */
public class MainActivity extends Activity {
    private static final int SECRET_TAP_COUNT = 5;
    private static final long SECRET_TAP_WINDOW_MS = 3000;
    private static final int SECRET_CORNER_DP = 80;
    private static final long RETRY_DELAY_MS = 10000;
    private static final int REQ_FILE_CHOOSER = 1;
    private static final int REQ_SETTINGS = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable retryLoad = this::reload;

    private KioskPrefs prefs;
    private WebView webView;
    private LinearLayout errorPanel;
    private TextView errorText;

    private String loadedUrl = "";
    private boolean mainFrameFailed;
    private int secretTaps;
    private long firstSecretTapAt;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new KioskPrefs(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(buildErrorPanel(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        configureWebView();
        applyFullscreen();

        if (prefs.url().isEmpty()) {
            openSettings(true);
        } else {
            loadHome();
        }
    }

    private void loadHome() {
        loadedUrl = prefs.url();
        webView.clearHistory();
        webView.loadUrl(loadedUrl);
    }

    private void openSettings(boolean firstRun) {
        startActivityForResult(new Intent(this, SettingsActivity.class)
                .putExtra(SettingsActivity.EXTRA_FIRST_RUN, firstRun), REQ_SETTINGS);
    }

    // ---------------------------------------------------------------- WebView

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setSupportMultipleWindows(false); // target=_blank links open in place

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    return false;
                }
                openExternally(uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainFrameFailed = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!mainFrameFailed) {
                    errorPanel.setVisibility(View.GONE);
                    handler.removeCallbacks(retryLoad);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showLoadError(String.valueOf(error.getDescription()));
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE_CHOOSER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) ->
                openExternally(Uri.parse(url)));
    }

    private View buildErrorPanel() {
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setBackgroundColor(Color.WHITE);
        int pad = dp(24);
        errorPanel.setPadding(pad, pad, pad, pad);
        errorPanel.setVisibility(View.GONE);
        errorPanel.setClickable(true);

        TextView title = new TextView(this);
        title.setText("Can't reach the WMS");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        errorPanel.addView(title);

        errorText = new TextView(this);
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        errorText.setTextColor(Color.DKGRAY);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(0, dp(12), 0, dp(20));
        errorPanel.addView(errorText);

        Button retry = new Button(this);
        retry.setText("Retry now");
        retry.setOnClickListener(v -> reload());
        errorPanel.addView(retry, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return errorPanel;
    }

    private void showLoadError(String description) {
        mainFrameFailed = true;
        errorText.setText(description + "\n\nCheck Wi-Fi. Retrying automatically every "
                + (RETRY_DELAY_MS / 1000) + " seconds…");
        errorPanel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(retryLoad);
        handler.postDelayed(retryLoad, RETRY_DELAY_MS);
    }

    private void reload() {
        handler.removeCallbacks(retryLoad);
        String current = webView.getUrl();
        if (current == null || current.isEmpty() || current.startsWith("data:")
                || current.startsWith("about:")) {
            loadHome();
        } else {
            webView.reload();
        }
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        if (requestCode == REQ_SETTINGS) {
            if (!prefs.url().equals(loadedUrl)) {
                loadHome();
            } else if (resultCode == SettingsActivity.RESULT_RELOAD) {
                reload();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ------------------------------------------------------------- Full screen

    private void applyFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) {
                return;
            }
            int bars = WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars();
            if (prefs.fullscreen()) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(bars);
            } else {
                controller.show(bars);
            }
        } else {
            View decor = getWindow().getDecorView();
            if (prefs.fullscreen()) {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            } else {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyFullscreen();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Relaunched from the boot receiver, launcher icon or Home button.
        applyFullscreen();
    }

    // ------------------------------------------------------ Hidden admin gesture

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int corner = dp(SECRET_CORNER_DP);
            View decor = getWindow().getDecorView();
            if (ev.getRawX() >= decor.getWidth() - corner && ev.getRawY() <= corner) {
                long now = SystemClock.elapsedRealtime();
                if (secretTaps == 0 || now - firstSecretTapAt > SECRET_TAP_WINDOW_MS) {
                    secretTaps = 0;
                    firstSecretTapAt = now;
                }
                if (++secretTaps >= SECRET_TAP_COUNT) {
                    secretTaps = 0;
                    showPinDialog();
                }
            } else {
                secretTaps = 0;
            }
        }
        // Never swallow the touch: the web page still receives it.
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        // Back navigates inside the WMS but never leaves the app.
        if (webView.canGoBack()) {
            webView.goBack();
        }
    }

    private void showPinDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("PIN");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Admin PIN")
                .setView(padded(input))
                .setPositiveButton("OK", (d, w) -> {
                    if (input.getText().toString().equals(prefs.pin())) {
                        openSettings(false);
                    } else {
                        Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        showWithKeyboard(dialog);
    }

    // ------------------------------------------------------------- Lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        applyFullscreen();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush(); // keep the WMS login across reboots
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        webView.destroy();
        super.onDestroy();
    }

    // --------------------------------------------------------------- Helpers

    private View padded(View child) {
        FrameLayout frame = new FrameLayout(this);
        int pad = dp(20);
        frame.setPadding(pad, dp(8), pad, 0);
        frame.addView(child);
        return frame;
    }

    private void showWithKeyboard(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
