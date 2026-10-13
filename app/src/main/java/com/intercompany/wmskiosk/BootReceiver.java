package com.intercompany.wmskiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Opens the WMS screen as soon as the scanner finishes booting. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!new KioskPrefs(context).autostart()) {
            return;
        }
        Intent launch = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launch);
    }
}
