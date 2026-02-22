package com.misalud.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

/**
 * Programa y cancela alarmas del sistema usando AlarmManager.
 * Lee los medicamentos desde SharedPreferences (sincronizados desde el HTML).
 */
public class AlarmScheduler {

    private static final String PREFS_NAME    = "MiSaludAlarms";
    private static final String KEY_MEDS      = "medicamentos";
    private static final String KEY_SOUND     = "tipoSonido";

    // ── Programar todas las alarmas activas ───────────────────────────────────
    public static void scheduleAll(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String medsJson = prefs.getString(KEY_MEDS, "[]");
            String tipoSonido = prefs.getString(KEY_SOUND, "suave");

            JSONArray meds = new JSONArray(medsJson);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            for (int i = 0; i < meds.length(); i++) {
                JSONObject med = meds.getJSONObject(i);
                long id     = med.getLong("id");
                String hora = med.optString("hora", "");
                String nombre = med.optString("nombre", "Medicamento");
                String dosis  = med.optString("dosis", "");
                String freq   = med.optString("freq", "diario");

                if (hora.isEmpty()) continue;

                String[] parts = hora.split(":");
                int hour = Integer.parseInt(parts[0]);
                int min  = Integer.parseInt(parts[1]);

                // Calcular próximo disparo
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, min);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                // Si ya pasó hoy, programar para mañana
                if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                }

                // Intent para el BroadcastReceiver
                Intent intent = new Intent(context, AlarmReceiver.class);
                intent.putExtra(AlarmReceiver.EXTRA_TITULO, "💊 " + nombre);
                intent.putExtra(AlarmReceiver.EXTRA_MENSAJE,
                        "Hora de tomar: " + nombre + (dosis.isEmpty() ? "" : " — " + dosis));
                intent.putExtra(AlarmReceiver.EXTRA_TIPO_SONIDO, tipoSonido);
                intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, (int)(id % Integer.MAX_VALUE));

                PendingIntent pi = PendingIntent.getBroadcast(
                        context,
                        (int)(id % Integer.MAX_VALUE),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                // Programar según frecuencia
                if ("semanal".equals(freq)) {
                    am.setRepeating(AlarmManager.RTC_WAKEUP,
                            cal.getTimeInMillis(),
                            AlarmManager.INTERVAL_DAY * 7,
                            pi);
                } else {
                    // Diario y todas las demás frecuencias — repetir cada 24h
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                                cal.getTimeInMillis(), pi);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                    } else {
                        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Cancelar todas las alarmas ────────────────────────────────────────────
    public static void cancelAll(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String medsJson = prefs.getString(KEY_MEDS, "[]");
            JSONArray meds = new JSONArray(medsJson);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            for (int i = 0; i < meds.length(); i++) {
                JSONObject med = meds.getJSONObject(i);
                long id = med.getLong("id");

                Intent intent = new Intent(context, AlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(
                        context,
                        (int)(id % Integer.MAX_VALUE),
                        intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Programar alarmas para turnos ─────────────────────────────────────────
    public static void scheduleTurnos(Context context, String turnosJson) {
        try {
            JSONArray turnos = new JSONArray(turnosJson);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            for (int i = 0; i < turnos.length(); i++) {
                JSONObject t = turnos.getJSONObject(i);
                String fecha = t.optString("fecha", "");
                String esp = t.optString("esp", "Turno médico");
                String hora = t.optString("hora", "09:00");
                String lugar = t.optString("lugar", "");
                long id = t.getLong("id");
                if (fecha.isEmpty()) continue;
                // Alarma el día anterior a las 9am
                String[] parts = hora.split(":");
                int hh = Integer.parseInt(parts[0]);
                int mm = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                try {
                    java.util.Date fechaDate = sdf.parse(fecha);
                    if (fechaDate == null) continue;
                    Calendar calDiaAntes = Calendar.getInstance();
                    calDiaAntes.setTime(fechaDate);
                    calDiaAntes.add(Calendar.DAY_OF_YEAR, -1);
                    calDiaAntes.set(Calendar.HOUR_OF_DAY, 9);
                    calDiaAntes.set(Calendar.MINUTE, 0);
                    calDiaAntes.set(Calendar.SECOND, 0);
                    if (calDiaAntes.getTimeInMillis() <= System.currentTimeMillis()) continue;
                    Intent intent = new Intent(context, AlarmReceiver.class);
                    intent.putExtra(AlarmReceiver.EXTRA_TITULO, "📅 Turno mañana: " + esp);
                    intent.putExtra(AlarmReceiver.EXTRA_MENSAJE, "Recordá: " + esp + " mañana" +
                        (hora.isEmpty() ? "" : " a las " + hora) + (lugar.isEmpty() ? "" : " en " + lugar));
                    intent.putExtra(AlarmReceiver.EXTRA_TIPO_SONIDO, "suave");
                    intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, (int)((id + 5000) % Integer.MAX_VALUE));
                    PendingIntent pi = PendingIntent.getBroadcast(context,
                        (int)((id + 5000) % Integer.MAX_VALUE), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calDiaAntes.getTimeInMillis(), pi);
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, calDiaAntes.getTimeInMillis(), pi);
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Guardar config desde JS ───────────────────────────────────────────────
    public static void saveMedicamentos(Context context, String jsonMeds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MEDS, jsonMeds).apply();
    }

    public static void saveTipoSonido(Context context, String tipo) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SOUND, tipo).apply();
    }
}
