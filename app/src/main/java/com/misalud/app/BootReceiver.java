package com.misalud.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Se ejecuta cuando el teléfono se reinicia.
 * Podría usarse para reprogramar alarmas si se implementa
 * AlarmManager en el futuro.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Reservado para futuras alarmas nativas con AlarmManager
        }
    }
}
