package com.misalud.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Programa alarmas nativas para TODOS los eventos con fecha:
 * medicamentos (hora exacta), turnos (día anterior 9am + hora exacta),
 * consultas (día anterior 9am), vacunas próximas dosis (una semana antes).
 */
public class AlarmScheduler {

    private static final String PREFS_NAME = "MiSaludAlarms";
    private static final String KEY_MEDS      = "medicamentos";
    private static final String KEY_TURNOS    = "turnos";
    private static final String KEY_CONSULTAS = "consultas";
    private static final String KEY_VACUNAS   = "vacunas";
    private static final String KEY_SOUND     = "tipoSonido";

    // ─────────────────────────────────────────────────────────────────────────
    //  GUARDAR DATOS DESDE JS
    // ─────────────────────────────────────────────────────────────────────────
    public static void saveMedicamentos(Context ctx, String json) {
        prefs(ctx).edit().putString(KEY_MEDS, json).apply();
    }
    public static void saveTurnos(Context ctx, String json) {
        prefs(ctx).edit().putString(KEY_TURNOS, json).apply();
    }
    public static void saveConsultas(Context ctx, String json) {
        prefs(ctx).edit().putString(KEY_CONSULTAS, json).apply();
    }
    public static void saveVacunas(Context ctx, String json) {
        prefs(ctx).edit().putString(KEY_VACUNAS, json).apply();
    }
    public static void saveTipoSonido(Context ctx, String tipo) {
        prefs(ctx).edit().putString(KEY_SOUND, tipo).apply();
    }
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PROGRAMAR TODO
    // ─────────────────────────────────────────────────────────────────────────
    public static void scheduleAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        SharedPreferences p = prefs(ctx);
        String sonido = p.getString(KEY_SOUND, "suave");

        scheduleMeds(ctx, am, p.getString(KEY_MEDS, "[]"), sonido);
        scheduleTurnos(ctx, am, p.getString(KEY_TURNOS, "[]"));
        scheduleConsultas(ctx, am, p.getString(KEY_CONSULTAS, "[]"));
        scheduleVacunas(ctx, am, p.getString(KEY_VACUNAS, "[]"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CANCELAR TODO
    // ─────────────────────────────────────────────────────────────────────────
    public static void cancelAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        SharedPreferences p = prefs(ctx);
        cancelJsonIds(ctx, am, p.getString(KEY_MEDS, "[]"), 0);
        cancelJsonIds(ctx, am, p.getString(KEY_TURNOS, "[]"), 10000);
        cancelJsonIds(ctx, am, p.getString(KEY_TURNOS, "[]"), 20000);   // alarma día exacto
        cancelJsonIds(ctx, am, p.getString(KEY_CONSULTAS, "[]"), 30000);
        cancelJsonIds(ctx, am, p.getString(KEY_VACUNAS, "[]"), 40000);
    }

    private static void cancelJsonIds(Context ctx, AlarmManager am, String json, int offset) {
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                long id = arr.getJSONObject(i).getLong("id");
                Intent intent = new Intent(ctx, AlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(ctx,
                        (int)((id + offset) % Integer.MAX_VALUE), intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) { am.cancel(pi); pi.cancel(); }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MEDICAMENTOS — alarma a la hora exacta, diario
    // ─────────────────────────────────────────────────────────────────────────
    private static void scheduleMeds(Context ctx, AlarmManager am, String json, String sonido) {
        try {
            JSONArray meds = new JSONArray(json);
            for (int i = 0; i < meds.length(); i++) {
                JSONObject m = meds.getJSONObject(i);
                long id     = m.getLong("id");
                String hora = m.optString("hora", "");
                String nombre = m.optString("nombre", "Medicamento");
                String dosis  = m.optString("dosis", "");
                if (hora.isEmpty()) continue;

                String[] parts = hora.split(":");
                int h = Integer.parseInt(parts[0]);
                int min = Integer.parseInt(parts[1]);

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, min);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                if (cal.getTimeInMillis() <= System.currentTimeMillis())
                    cal.add(Calendar.DAY_OF_YEAR, 1);

                Intent intent = new Intent(ctx, AlarmReceiver.class);
                intent.putExtra(AlarmReceiver.EXTRA_TITULO, "💊 " + nombre);
                intent.putExtra(AlarmReceiver.EXTRA_MENSAJE,
                        "Hora de tomar: " + nombre + (dosis.isEmpty() ? "" : " — " + dosis));
                intent.putExtra(AlarmReceiver.EXTRA_TIPO_SONIDO, sonido);
                intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, (int)(id % Integer.MAX_VALUE));

                setExact(am, cal.getTimeInMillis(),
                        PendingIntent.getBroadcast(ctx, (int)(id % Integer.MAX_VALUE), intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TURNOS — aviso el día anterior a las 9am + aviso 2h antes del turno
    // ─────────────────────────────────────────────────────────────────────────
    private static void scheduleTurnos(Context ctx, AlarmManager am, String json) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.getJSONObject(i);
                String fecha = t.optString("fecha", "");
                String esp   = t.optString("esp", "Turno médico");
                String hora  = t.optString("hora", "");
                String lugar = t.optString("lugar", "");
                long id = t.getLong("id");
                if (fecha.isEmpty()) continue;

                Date fechaDate = sdf.parse(fecha);
                if (fechaDate == null) continue;

                // — Aviso día anterior a las 9:00 —
                Calendar vispera = Calendar.getInstance();
                vispera.setTime(fechaDate);
                vispera.add(Calendar.DAY_OF_YEAR, -1);
                vispera.set(Calendar.HOUR_OF_DAY, 9);
                vispera.set(Calendar.MINUTE, 0);
                vispera.set(Calendar.SECOND, 0);

                if (vispera.getTimeInMillis() > System.currentTimeMillis()) {
                    String sub = hora.isEmpty() ? "" : " a las " + hora;
                    String lug = lugar.isEmpty() ? "" : " en " + lugar;
                    Intent in1 = new Intent(ctx, AlarmReceiver.class);
                    in1.putExtra(AlarmReceiver.EXTRA_TITULO, "📅 Turno mañana: " + esp);
                    in1.putExtra(AlarmReceiver.EXTRA_MENSAJE, "Recordá tu turno de " + esp + " mañana" + sub + lug);
                    in1.putExtra(AlarmReceiver.EXTRA_TIPO_SONIDO, "suave");
                    in1.putExtra(AlarmReceiver.EXTRA_ALARM_ID, (int)((id + 10000) % Integer.MAX_VALUE));
                    setExact(am, vispera.getTimeInMillis(),
                            PendingIntent.getBroadcast(ctx, (int)((id + 10000) % Integer.MAX_VALUE), in1,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                }

                // — Aviso 2 horas antes del turno (si tiene hora) —
                if (!hora.isEmpty()) {
                    String[] hp = hora.split(":");
                    Calendar calTurno = Calendar.getInstance();
                    calTurno.setTime(fechaDate);
                    calTurno.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hp[0]));
                    calTurno.set(Calendar.MINUTE, Integer.parseInt(hp[1]));
                    calTurno.set(Calendar.SECOND, 0);
                    calTurno.add(Calendar.HOUR_OF_DAY, -2);

                    if (calTurno.getTimeInMillis() > System.currentTimeMillis()) {
                        Intent in2 = new Intent(ctx, AlarmReceiver.class);
                        in2.putExtra(AlarmReceiver.EXTRA_TITULO, "📅 Turno en 2 horas: " + esp);
                        in2.putExtra(AlarmReceiver.EXTRA_MENSAJE,
                                "Tu turno de " + esp + " es a las " + hora + (lugar.isEmpty() ? "" : " en " + lugar));
                        in2.putExtra(AlarmReceiver.EXTRA_TIPO_SONIDO, "suave");
                        in2.putExtra(AlarmReceiver.EXTRA_ALARM_ID, (int)((id + 20000) % Integer.MAX_VALUE));
                        setExact(am, calTurno.getTimeInMillis(),
                                PendingIntent.getBroadcast(ctx, (int)((id + 20000) % Integer.MAX_VALUE), in2,
                                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONSULTAS — aviso el día anterior a las 9am
    // ─────────────────────────────────────────────────────────────────────────
    private static void scheduleConsultas(Context ctx, AlarmManager am, String json) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject cc = arr.getJSONObject(i);
                String prox  = cc.optString("prox", "");
                String esp   = cc.optString("esp", "Consulta");
                String medico = cc.optString("medico", "");
                long id = cc.getLong("id");
                if (prox.isEmpty()) continue;

                Date fechaDate = sdf.parse(prox);
                if (fechaDate == null) continue;

                Calendar vispera = Calendar.getInstance();
                vispera.setTime(fechaDate);
                vispera.add(Calendar.DAY_OF_YEAR, -1);
                vispera.set(Calendar.HOUR_OF_DAY, 9);
                vispera.set(Calendar.MINUTE, 0);
                vispera.set(Calendar.SECOND, 0);

                if (vispera.getTimeInMillis() <= System.currentTimeMillis()) continue;

                Intent intent = new Intent(ctx, AlarmReceiver.class);
                intent.putExtra(AlarmReceiver.EXTRA_TITULO, "🏥 Consulta mañana: " + esp);
                intent.putExtra(AlarmReceiver.EXTRA_MENSAJE,
                        "Recordá tu consulta de " + esp + (medico.isEmpty() ? "" : " con " + medico) + " mañana");
                intent.putExtra(AlarmReceiver.EXTRA_TIPO_SONIDO, "suave");
                intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, (int)((id + 30000) % Integer.MAX_VALUE));
                setExact(am, vispera.getTimeInMillis(),
                        PendingIntent.getBroadcast(ctx, (int)((id + 30000) % Integer.MAX_VALUE), intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VACUNAS — aviso 7 días antes de la próxima dosis, a las 9am
    // ─────────────────────────────────────────────────────────────────────────
    private static void scheduleVacunas(Context ctx, AlarmManager am, String json) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject v = arr.getJSONObject(i);
                String prox   = v.optString("prox", "");
                String nombre = v.optString("nombre", v.optString("vacuna", "Vacuna"));
                long id = v.getLong("id");
                if (prox.isEmpty()) continue;

                Date fechaDate = sdf.parse(prox);
                if (fechaDate == null) continue;

                Calendar semanaAntes = Calendar.getInstance();
                semanaAntes.setTime(fechaDate);
                semanaAntes.add(Calendar.DAY_OF_YEAR, -7);
                semanaAntes.set(Calendar.HOUR_OF_DAY, 9);
                semanaAntes.set(Calendar.MINUTE, 0);
                semanaAntes.set(Calendar.SECOND, 0);

                if (semanaAntes.getTimeInMillis() <= System.currentTimeMillis()) continue;

                Intent intent = new Intent(ctx, AlarmReceiver.class);
                intent.putExtra(AlarmReceiver.EXTRA_TITULO, "💉 Vacuna en 7 días: " + nombre);
                intent.putExtra(AlarmReceiver.EXTRA_MENSAJE,
                        "Tu próxima dosis de " + nombre + " es el " + prox + ". Coordiná con tu médico.");
                intent.putExtra(AlarmReceiver.EXTRA_TIPO_SONIDO, "suave");
                intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, (int)((id + 40000) % Integer.MAX_VALUE));
                setExact(am, semanaAntes.getTimeInMillis(),
                        PendingIntent.getBroadcast(ctx, (int)((id + 40000) % Integer.MAX_VALUE), intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPER — setExactAndAllowWhileIdle con fallback
    // ─────────────────────────────────────────────────────────────────────────
    private static void setExact(AlarmManager am, long timeMs, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(AlarmManager.RTC_WAKEUP, timeMs, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, timeMs, pi);
        }
    }
}
