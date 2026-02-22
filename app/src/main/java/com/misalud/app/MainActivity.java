package com.misalud.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST  = 1;
    private static final int PERMISSION_REQUEST    = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Crear canales de notificación al iniciar
        AlarmReceiver.createChannels(this);

        webView = findViewById(R.id.webview);

        // ── WebSettings ──────────────────────────────────────────────────────
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        // ── Puente JS ↔ Android ───────────────────────────────────────────────
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        // ── WebViewClient ─────────────────────────────────────────────────────
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith("tel:")) {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url)));
                    return true;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });

        // ── WebChromeClient ───────────────────────────────────────────────────
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> filePath,
                    FileChooserParams params) {
                filePathCallback = filePath;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                Intent chooser = Intent.createChooser(intent, "Seleccionar imagen");
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                return true;
            }
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) { return true; }
        });

        requestAppPermissions();
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PUENTE JavaScript ↔ Android
    // ═══════════════════════════════════════════════════════════════════════════
    public class AndroidBridge {

        /**
         * Llamado desde JS cuando el usuario guarda/modifica medicamentos.
         * Reprograma todas las alarmas nativas.
         * @param medicamentosJson  JSON string con array de medicamentos
         */
        @JavascriptInterface
        public void actualizarAlarmas(String medicamentosJson) {
            AlarmScheduler.cancelAll(getApplicationContext());
            AlarmScheduler.saveMedicamentos(getApplicationContext(), medicamentosJson);
            AlarmScheduler.scheduleAll(getApplicationContext());
        }

        /**
         * Llamado desde JS cuando el usuario cambia tipo de sonido (suave/intenso).
         */
        @JavascriptInterface
        public void setTipoSonido(String tipo) {
            AlarmScheduler.saveTipoSonido(getApplicationContext(), tipo);
            // Reprogramar con nuevo sonido
            AlarmScheduler.cancelAll(getApplicationContext());
            AlarmScheduler.scheduleAll(getApplicationContext());
        }

        /**
         * Llamado desde JS para probar el sonido directamente.
         */
        @JavascriptInterface
        public void probarSonido(String tipo) {
            AlarmReceiver.playSound(getApplicationContext(), tipo);
            // Parar el sonido de prueba después de 3 segundos
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(AlarmReceiver::stopSound, 3000);
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            android.os.Vibrator v = (android.os.Vibrator)
                    getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(
                        ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        }

        @JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public String getVersion() { return "4.0"; }
    }

    // ── Permisos ──────────────────────────────────────────────────────────────
    private void requestAppPermissions() {
        List<String> needed = new ArrayList<>();
        String[] wanted = {
                Manifest.permission.VIBRATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.SCHEDULE_EXACT_ALARM,
        };
        // Android 13+: permiso de notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        for (String p : wanted) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;
            if (res == Activity.RESULT_OK && data != null) {
                String ds = data.getDataString();
                if (ds != null) results = new Uri[]{Uri.parse(ds)};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onPause()   { super.onPause();   webView.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); webView.destroy(); }
}
