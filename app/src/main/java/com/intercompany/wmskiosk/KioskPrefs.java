package com.intercompany.wmskiosk;

import android.content.Context;
import android.content.SharedPreferences;

/** Device-local settings, edited from the Settings screen. */
final class KioskPrefs {
    private static final String FILE = "kiosk";
    private static final String KEY_URL = "wms_url";
    private static final String KEY_PIN = "admin_pin";
    private static final String KEY_AUTOSTART = "autostart";
    private static final String KEY_FULLSCREEN = "fullscreen";
    private static final String KEY_LAUNCHER_MODE = "launcher_mode";

    private final SharedPreferences prefs;

    KioskPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    String url() {
        return prefs.getString(KEY_URL, BuildConfig.DEFAULT_WMS_URL);
    }

    void setUrl(String url) {
        prefs.edit().putString(KEY_URL, url).apply();
    }

    String pin() {
        return prefs.getString(KEY_PIN, BuildConfig.DEFAULT_ADMIN_PIN);
    }

    void setPin(String pin) {
        prefs.edit().putString(KEY_PIN, pin).apply();
    }

    boolean autostart() {
        return prefs.getBoolean(KEY_AUTOSTART, true);
    }

    void setAutostart(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTOSTART, enabled).apply();
    }

    boolean fullscreen() {
        return prefs.getBoolean(KEY_FULLSCREEN, true);
    }

    void setFullscreen(boolean enabled) {
        prefs.edit().putBoolean(KEY_FULLSCREEN, enabled).apply();
    }

    boolean launcherMode() {
        return prefs.getBoolean(KEY_LAUNCHER_MODE, false);
    }

    void setLauncherMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_LAUNCHER_MODE, enabled).apply();
    }
}
