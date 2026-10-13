package com.intercompany.wmskiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Setup screen. Opens automatically on first launch (no WMS address saved yet) and afterwards
 * from the hidden admin gesture in {@link MainActivity}.
 */
public class SettingsActivity extends Activity {
    static final String EXTRA_FIRST_RUN = "first_run";
    static final int RESULT_RELOAD = RESULT_FIRST_USER;

    private static final int NAVY = Color.parseColor("#12355B");

    private KioskPrefs prefs;
    private boolean firstRun;

    private EditText urlInput;
    private TextView testResult;
    private View autostartPermissionRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new KioskPrefs(this);
        firstRun = getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        form.setPadding(pad, pad, pad, pad);

        TextView title = text(firstRun ? "Set up Xoro WMS" : "Xoro WMS settings", 24, Color.BLACK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(title);
        form.addView(text("Version " + BuildConfig.VERSION_NAME, 13, Color.GRAY));
        if (firstRun) {
            form.addView(spaced(text("Enter the WMS web address, then tap Save & Launch. "
                    + "To come back to this screen later, tap the top-right corner of the screen "
                    + "5 times quickly and enter the admin PIN"
                    + (prefs.pin().equals("1234") ? " (default 1234)." : "."), 15, Color.DKGRAY), 12));
        }

        // --- WMS address
        form.addView(sectionHeader("WMS address"));
        urlInput = new EditText(this);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setHint("https://your-wms-address");
        urlInput.setSingleLine(true);
        urlInput.setText(prefs.url());
        form.addView(urlInput);

        Button test = button("Test connection", false);
        test.setOnClickListener(v -> testConnection());
        form.addView(test, wrap(4));
        testResult = text("", 14, Color.DKGRAY);
        form.addView(testResult);

        // --- Behaviour
        form.addView(sectionHeader("Behaviour"));
        form.addView(toggle("Full screen",
                "Hide Android's status and navigation bars",
                prefs.fullscreen(), (b, on) -> prefs.setFullscreen(on)));

        form.addView(toggle("Open automatically when the scanner starts",
                "Launches the WMS right after the scanner turns on",
                prefs.autostart(), (b, on) -> {
                    prefs.setAutostart(on);
                    refreshPermissionRow();
                }));

        autostartPermissionRow = buildPermissionRow();
        form.addView(autostartPermissionRow);

        form.addView(toggle("Use as Home screen (launcher mode)",
                "The Home button and startup always open the WMS. Turn off to get the normal Android home screen back",
                prefs.launcherMode(), (b, on) -> setLauncherMode(on, true)));

        // --- Actions
        Button save = button("Save & Launch", true);
        save.setOnClickListener(v -> saveAndLaunch());
        form.addView(save, fill(24));

        form.addView(sectionHeader("Admin"));
        Button pin = button("Change admin PIN", false);
        pin.setOnClickListener(v -> showChangePinDialog());
        form.addView(pin, fill(0));

        if (!firstRun) {
            Button reload = button("Reload WMS page", false);
            reload.setOnClickListener(v -> {
                setResult(RESULT_RELOAD);
                finish();
            });
            form.addView(reload, fill(0));
        }

        Button android = button("Open Android settings", false);
        android.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        form.addView(android, fill(0));

        Button close = button("Close app", false);
        close.setOnClickListener(v -> closeApp());
        form.addView(close, fill(0));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.addView(form);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionRow(); // the user may be coming back from the permission screen
    }

    @Override
    public void onBackPressed() {
        if (firstRun && prefs.url().isEmpty()) {
            Toast.makeText(this, "Enter the WMS address first, or tap Close app", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------- Actions

    private void saveAndLaunch() {
        String url = normalisedUrl();
        if (url == null) {
            return;
        }
        prefs.setUrl(url);
        setResult(RESULT_OK);
        finish();
    }

    /** Returns the address with a scheme, or null (and shows an error) when it is empty. */
    private String normalisedUrl() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            urlInput.setError("Enter the WMS address");
            urlInput.requestFocus();
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
            urlInput.setText(url);
        }
        return url;
    }

    private void testConnection() {
        String url = normalisedUrl();
        if (url == null) {
            return;
        }
        testResult.setTextColor(Color.DKGRAY);
        testResult.setText("Testing…");
        new Thread(() -> {
            String message;
            boolean ok;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                int code = connection.getResponseCode();
                ok = code < 500;
                message = (ok ? "✓ Reached the WMS" : "✗ Server error") + " (HTTP " + code + ")";
            } catch (Exception e) {
                ok = false;
                message = "✗ Can't reach it: " + e.getClass().getSimpleName()
                        + (e.getMessage() != null ? " – " + e.getMessage() : "");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            boolean success = ok;
            String result = message;
            runOnUiThread(() -> {
                testResult.setTextColor(success ? Color.parseColor("#1B7F3B") : Color.parseColor("#B3261E"));
                testResult.setText(result);
            });
        }).start();
    }

    private void closeApp() {
        if (prefs.launcherMode()) {
            new AlertDialog.Builder(this)
                    .setTitle("Close app")
                    .setMessage("Xoro WMS is the Home screen, so it will open again when Home is pressed.\n\n"
                            + "Turn off Home-screen mode to get the normal Android home screen back?")
                    .setPositiveButton("Turn off & close", (d, w) -> {
                        setLauncherMode(false, false);
                        finishAndRemoveTask();
                    })
                    .setNeutralButton("Just close", (d, w) -> finishAndRemoveTask())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            finishAndRemoveTask(); // closes the WMS screen too: same task
        }
    }

    private void showChangePinDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("New PIN (at least 4 digits)");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, fill(0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Change admin PIN")
                .setView(box)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = input.getText().toString();
            if (pin.length() < 4) {
                input.setError("At least 4 digits");
                return;
            }
            prefs.setPin(pin);
            dialog.dismiss();
            Toast.makeText(this, "PIN changed", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    // ------------------------------------------------------- Launcher mode

    static ComponentName homeAlias(Context context) {
        return new ComponentName(context, MainActivity.class.getPackage().getName() + ".HomeAlias");
    }

    private void setLauncherMode(boolean enabled, boolean showHomeSettings) {
        prefs.setLauncherMode(enabled);
        getPackageManager().setComponentEnabledSetting(homeAlias(this),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        if (!showHomeSettings) {
            return;
        }
        String message = enabled
                ? "On the next screen choose \"Xoro WMS\" as the Home app (if Android asks, pick \"Always\").\n\n"
                  + "To undo it later, turn this switch off again."
                : "Home-screen mode is off. If Android asks which Home app to use, choose the normal "
                  + "launcher (e.g. \"Launcher3\" or \"Quickstep\").";
        new AlertDialog.Builder(this)
                .setTitle(enabled ? "Use as Home screen" : "Home screen restored")
                .setMessage(message)
                .setPositiveButton("Open Home settings", (d, w) -> openHomeSettings())
                .setNegativeButton("OK", null)
                .show();
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // --------------------------------------------- Auto-start permission (10+)

    static boolean needsOverlayPermission(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(context);
    }

    private View buildPermissionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(12));
        row.setBackgroundColor(Color.parseColor("#FFF4E0"));
        row.addView(text("Android needs \"Display over other apps\" turned ON for Xoro WMS "
                + "so it can open by itself at startup. On the next screen switch it on, then press Back.",
                14, Color.parseColor("#5C3D00")));
        Button allow = button("Allow opening at startup", true);
        allow.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        row.addView(allow, wrap(8));
        return row;
    }

    private void refreshPermissionRow() {
        autostartPermissionRow.setVisibility(
                prefs.autostart() && needsOverlayPermission(this) ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------- Views

    private View toggle(String label, String help, boolean checked,
                        CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(6));
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        toggle.setTextColor(Color.BLACK);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle, fill(0));
        row.addView(text(help, 13, Color.GRAY));
        return row;
    }

    private TextView sectionHeader(String label) {
        TextView header = text(label.toUpperCase(), 13, NAVY);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, dp(22), 0, dp(4));
        return header;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        if (primary) {
            button.setBackgroundColor(NAVY);
            button.setTextColor(Color.WHITE);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            button.setPadding(dp(16), dp(14), dp(16), dp(14));
        }
        return button;
    }

    private <T extends View> T spaced(T view, int topDp) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop() + dp(topDp),
                view.getPaddingRight(), view.getPaddingBottom());
        return view;
    }

    private LinearLayout.LayoutParams fill(int topMarginDp) {
        return params(ViewGroup.LayoutParams.MATCH_PARENT, topMarginDp);
    }

    private LinearLayout.LayoutParams wrap(int topMarginDp) {
        return params(ViewGroup.LayoutParams.WRAP_CONTENT, topMarginDp);
    }

    private LinearLayout.LayoutParams params(int width, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
