package com.misalud.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class AlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID_ALARMA   = "misalud_alarma";
    public static final String CHANNEL_ID_SUAVE    = "misalud_suave";
    public static final String EXTRA_TITULO        = "titulo";
    public static final String EXTRA_MENSAJE       = "mensaje";
    public static final String EXTRA_TIPO_SONIDO   = "tipoSonido"; // "intenso" o "suave"
    public static final String EXTRA_ALARM_ID      = "alarmId";
    public static final String ACTION_DISMISS      = "com.misalud.app.DISMISS_ALARM";

    private static MediaPlayer mediaPlayer;

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        // Acción de apagar la alarma desde el botón de la notificación
        if (ACTION_DISMISS.equals(action)) {
            stopSound();
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            int alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 1);
            if (nm != null) nm.cancel(alarmId);
            return;
        }

        // Recibir datos de la alarma
        String titulo     = intent.getStringExtra(EXTRA_TITULO);
        String mensaje    = intent.getStringExtra(EXTRA_MENSAJE);
        String tipoSonido = intent.getStringExtra(EXTRA_TIPO_SONIDO);
        int alarmId       = intent.getIntExtra(EXTRA_ALARM_ID, 1);

        if (titulo == null) titulo = "💊 Hora del medicamento";
        if (mensaje == null) mensaje = "Es momento de tomar tu medicamento";
        if (tipoSonido == null) tipoSonido = "suave";

        // Crear canales de notificación (Android 8+)
        createChannels(context);

        // Reproducir sonido
        playSound(context, tipoSonido);

        // Vibrar
        vibrate(context, tipoSonido);

        // Intent para abrir la app al tocar la notificación
        Intent openApp = new Intent(context, SplashActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingOpen = PendingIntent.getActivity(
                context, alarmId, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent para apagar la alarma desde la notificación
        Intent dismissIntent = new Intent(context, AlarmReceiver.class);
        dismissIntent.setAction(ACTION_DISMISS);
        dismissIntent.putExtra(EXTRA_ALARM_ID, alarmId);
        PendingIntent pendingDismiss = PendingIntent.getBroadcast(
                context, alarmId + 1000, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Elegir canal según tipo de sonido
        String channelId = "intenso".equals(tipoSonido) ? CHANNEL_ID_ALARMA : CHANNEL_ID_SUAVE;

        // Construir notificación
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setOngoing(true)           // No se puede deslizar, obliga a tocar "Apagar"
                .setContentIntent(pendingOpen)
                .addAction(android.R.drawable.ic_delete, "🔕 Apagar alarma", pendingDismiss)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Mostrar notificación
        NotificationManagerCompat notifManager = NotificationManagerCompat.from(context);
        try {
            notifManager.notify(alarmId, builder.build());
        } catch (SecurityException e) {
            // Sin permiso de notificaciones (Android 13+)
            e.printStackTrace();
        }
    }

    // ── Reproducir sonido ──────────────────────────────────────────────────────
    public static void playSound(Context context, String tipo) {
        stopSound();
        try {
            mediaPlayer = new MediaPlayer();
            Uri soundUri;

            if ("intenso".equals(tipo)) {
                // Alarma del sistema (muy intensa)
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (soundUri == null) {
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
            } else {
                // Notificación suave del sistema
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage("intenso".equals(tipo)
                            ? AudioAttributes.USAGE_ALARM
                            : AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            mediaPlayer.setAudioAttributes(aa);
            mediaPlayer.setDataSource(context, soundUri);
            mediaPlayer.setLooping("intenso".equals(tipo)); // Intenso: repite hasta apagar
            mediaPlayer.prepare();
            mediaPlayer.start();

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback: usar ringtone directo
            try {
                Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                android.media.Ringtone r = RingtoneManager.getRingtone(context, fallback);
                if (r != null) r.play();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void stopSound() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaPlayer = null;
        }
    }

    // ── Vibración ─────────────────────────────────────────────────────────────
    private void vibrate(Context context, String tipo) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;

        long[] patronIntenso = {0, 800, 200, 800, 200, 800};
        long[] patronSuave   = {0, 400, 200, 400};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] patron = "intenso".equals(tipo) ? patronIntenso : patronSuave;
            int repeat = "intenso".equals(tipo) ? 0 : -1; // 0=repetir, -1=una vez
            v.vibrate(VibrationEffect.createWaveform(patron, repeat));
        } else {
            long[] patron = "intenso".equals(tipo) ? patronIntenso : patronSuave;
            v.vibrate(patron, "intenso".equals(tipo) ? 0 : -1);
        }
    }

    // ── Crear canales de notificación ─────────────────────────────────────────
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Canal INTENSO — máxima prioridad, usa sonido de alarma
        if (nm.getNotificationChannel(CHANNEL_ID_ALARMA) == null) {
            NotificationChannel chAlarma = new NotificationChannel(
                    CHANNEL_ID_ALARMA,
                    "Alarma de medicamento",
                    NotificationManager.IMPORTANCE_HIGH);
            chAlarma.setDescription("Recordatorio urgente de medicamentos");
            chAlarma.enableVibration(true);
            chAlarma.setVibrationPattern(new long[]{0, 800, 200, 800, 200, 800});
            chAlarma.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound != null) {
                AudioAttributes aa = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                chAlarma.setSound(alarmSound, aa);
            }
            nm.createNotificationChannel(chAlarma);
        }

        // Canal SUAVE — prioridad alta, usa sonido de notificación
        if (nm.getNotificationChannel(CHANNEL_ID_SUAVE) == null) {
            NotificationChannel chSuave = new NotificationChannel(
                    CHANNEL_ID_SUAVE,
                    "Recordatorio suave",
                    NotificationManager.IMPORTANCE_HIGH);
            chSuave.setDescription("Recordatorio tranquilo de medicamentos");
            chSuave.enableVibration(true);
            chSuave.setVibrationPattern(new long[]{0, 400, 200, 400});
            chSuave.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            Uri notifSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (notifSound != null) {
                AudioAttributes aa = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                chSuave.setSound(notifSound, aa);
            }
            nm.createNotificationChannel(chSuave);
        }
    }
}
