package com.misalud.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Se ejecuta cuando el teléfono se reinicia.
 * Reprograma todas las alarmas para que sobrevivan al reinicio.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            // Reprogramar todas las alarmas guardadas
            AlarmReceiver.createChannels(context);
            AlarmScheduler.scheduleAll(context);
        }
    }
}
