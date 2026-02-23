package com.misalud.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
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
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1;
    private static final int PERMISSION_REQUEST   = 2;
    private static final int INSTALL_PERMISSION   = 3;
    private boolean fileChooserForJson = false;

    // APK download tracking
    private long downloadId = -1;
    private String pendingApkPath = "";

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadId) {
                // Query download status
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(downloadId);
                android.database.Cursor cursor = dm.query(q);
                if (cursor != null && cursor.moveToFirst()) {
                    int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusCol);
                    int localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                    String localUri = cursor.getString(localUriCol);
                    cursor.close();
                    if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                        pendingApkPath = localUri.replace("file://", "");
                        runOnUiThread(() -> {
                            webView.evaluateJavascript(
                                "onApkDescargado('" + pendingApkPath + "')", null);
                        });
                    } else {
                        runOnUiThread(() -> {
                            webView.evaluateJavascript("onApkError('Descarga fallida')", null);
                        });
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AlarmReceiver.createChannels(this);

        // Register download receiver
        registerReceiver(downloadReceiver,
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        webView = findViewById(R.id.webview);
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

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

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

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback cb) { cb.invoke(origin, true, false); }
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> filePath, FileChooserParams params) {
                filePathCallback = filePath;
                if (fileChooserForJson) {
                    fileChooserForJson = false;
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"application/json","text/plain","*/*"});
                    startActivityForResult(
                        Intent.createChooser(intent, "Seleccionar respaldo .json"),
                        FILE_CHOOSER_REQUEST);
                } else {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    Intent chooser = Intent.createChooser(intent, "Seleccionar imagen");
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                    startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                }
                return true;
            }
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) { return true; }
        });

        requestAppPermissions();
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ═══════════════════════════════════════════════════════════════
    //  JAVASCRIPT BRIDGE
    // ═══════════════════════════════════════════════════════════════
    public class AndroidBridge {

        /** Descarga el APK desde la URL y reporta progreso a JS */
        @JavascriptInterface
        public void descargarAPK(String apkUrl) {
            runOnUiThread(() -> {
                try {
                    // Verificar permiso de instalación de fuentes desconocidas (Android 8+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (!getPackageManager().canRequestPackageInstalls()) {
                            // Pedir permiso — cuando regrese, JS reintentará
                            Intent intent = new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, INSTALL_PERMISSION);
                            webView.evaluateJavascript(
                                "onApkError('Primero habilitá la instalación desde fuentes desconocidas y volvé a tocar Actualizar')", null);
                            return;
                        }
                    }

                    // Borrar APK anterior si existe
                    File apkFile = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "MiSalud-update.apk");
                    if (apkFile.exists()) apkFile.delete();

                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
                    request.setTitle("Mi Salud — Actualizando...");
                    request.setDescription("Descargando nueva versión");
                    request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE);
                    request.setDestinationPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, "MiSalud-update.apk");
                    request.setMimeType("application/vnd.android.package-archive");
                    request.allowScanningByMediaScanner();

                    downloadId = dm.enqueue(request);

                    // Poll progress in background thread
                    new Thread(() -> {
                        DownloadManager dmPoll = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        boolean downloading = true;
                        while (downloading) {
                            DownloadManager.Query q = new DownloadManager.Query();
                            q.setFilterById(downloadId);
                            android.database.Cursor cursor = dmPoll.query(q);
                            if (cursor != null && cursor.moveToFirst()) {
                                int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                                int downloadedCol = cursor.getColumnIndex(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                                int totalCol = cursor.getColumnIndex(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                                int status = cursor.getInt(statusCol);
                                long downloaded = cursor.getLong(downloadedCol);
                                long total = cursor.getLong(totalCol);
                                cursor.close();

                                if (total > 0) {
                                    int pct = (int)((downloaded * 100) / total);
                                    runOnUiThread(() ->
                                        webView.evaluateJavascript(
                                            "onApkProgreso(" + pct + ")", null));
                                }
                                if (status == DownloadManager.STATUS_SUCCESSFUL ||
                                    status == DownloadManager.STATUS_FAILED) {
                                    downloading = false;
                                }
                            } else { downloading = false; }
                            try { Thread.sleep(500); } catch (Exception e) { break; }
                        }
                    }).start();

                } catch (Exception e) {
                    webView.evaluateJavascript(
                        "onApkError('" + e.getMessage() + "')", null);
                }
            });
        }

        /** Instala el APK ya descargado */
        @JavascriptInterface
        public void instalarAPK(String apkPath) {
            runOnUiThread(() -> {
                try {
                    File apkFile = new File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS),
                        "MiSalud-update.apk");

                    if (!apkFile.exists()) {
                        webView.evaluateJavascript(
                            "onApkError('No se encontró el archivo descargado')", null);
                        return;
                    }

                    Uri apkUri;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        apkUri = FileProvider.getUriForFile(MainActivity.this,
                            getPackageName() + ".provider", apkFile);
                    } else {
                        apkUri = Uri.fromFile(apkFile);
                    }

                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(apkUri,
                        "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(install);
                } catch (Exception e) {
                    webView.evaluateJavascript(
                        "onApkError('" + e.getMessage() + "')", null);
                }
            });
        }

        /** Sincronizar TODOS los datos para alarmas */
        @JavascriptInterface
        public void sincronizarTodo(String medsJson, String turnosJson,
                                    String consultasJson, String vacunasJson) {
            AlarmScheduler.cancelAll(getApplicationContext());
            AlarmScheduler.saveMedicamentos(getApplicationContext(), medsJson);
            AlarmScheduler.saveTurnos(getApplicationContext(), turnosJson);
            AlarmScheduler.saveConsultas(getApplicationContext(), consultasJson);
            AlarmScheduler.saveVacunas(getApplicationContext(), vacunasJson);
            AlarmScheduler.scheduleAll(getApplicationContext());
        }

        @JavascriptInterface
        public void actualizarAlarmas(String medsJson) {
            AlarmScheduler.saveMedicamentos(getApplicationContext(), medsJson);
            AlarmScheduler.cancelAll(getApplicationContext());
            AlarmScheduler.scheduleAll(getApplicationContext());
        }

        @JavascriptInterface
        public void actualizarTurnos(String json) {
            AlarmScheduler.saveTurnos(getApplicationContext(), json);
            AlarmScheduler.cancelAll(getApplicationContext());
            AlarmScheduler.scheduleAll(getApplicationContext());
        }

        @JavascriptInterface
        public void actualizarConsultas(String json) {
            AlarmScheduler.saveConsultas(getApplicationContext(), json);
            AlarmScheduler.cancelAll(getApplicationContext());
            AlarmScheduler.scheduleAll(getApplicationContext());
        }

        @JavascriptInterface
        public void actualizarVacunas(String json) {
            AlarmScheduler.saveVacunas(getApplicationContext(), json);
            AlarmScheduler.cancelAll(getApplicationContext());
            AlarmScheduler.scheduleAll(getApplicationContext());
        }

        @JavascriptInterface
        public void setTipoSonido(String tipo) {
            AlarmScheduler.saveTipoSonido(getApplicationContext(), tipo);
            AlarmScheduler.cancelAll(getApplicationContext());
            AlarmScheduler.scheduleAll(getApplicationContext());
        }

        @JavascriptInterface
        public void probarSonido(String tipo) {
            AlarmReceiver.playSound(getApplicationContext(), tipo);
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(AlarmReceiver::stopSound, 3000);
        }

        @JavascriptInterface
        public void mostrarNotificacion(String titulo, String cuerpo, String tipo) {
            AlarmReceiver.createChannels(getApplicationContext());
            Intent intent = new Intent(getApplicationContext(), AlarmReceiver.class);
            intent.putExtra(AlarmReceiver.EXTRA_TITULO, titulo);
            intent.putExtra(AlarmReceiver.EXTRA_MENSAJE, cuerpo);
            intent.putExtra(AlarmReceiver.EXTRA_TIPO_SONIDO, "suave");
            intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID,
                    (int)(System.currentTimeMillis() % Integer.MAX_VALUE));
            getApplicationContext().sendBroadcast(intent);
        }

        @JavascriptInterface
        public String guardarRespaldo(String jsonContent, String nombreArchivo) {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, nombreArchivo);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(jsonContent.getBytes("UTF-8"));
                fos.close();
                return file.getAbsolutePath();
            } catch (Exception e) { e.printStackTrace(); return ""; }
        }

        @JavascriptInterface
        public void abrirSelectorJson() {
            runOnUiThread(() -> {
                fileChooserForJson = true;
                webView.evaluateJavascript(
                    "document.getElementById('importInput').click();", null);
            });
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(
                        ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else { v.vibrate(ms); }
        }

        @JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public String getVersion() { return "9.0"; }
    }

    private void requestAppPermissions() {
        List<String> needed = new ArrayList<>();
        String[] wanted = {
            Manifest.permission.VIBRATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        for (String p : wanted) {
            try {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                    needed.add(p);
            } catch (Exception ignored) {}
        }
        if (!needed.isEmpty())
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERMISSION_REQUEST);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;
            if (res == Activity.RESULT_OK && data != null) {
                String ds = data.getDataString();
                if (ds != null) results = new Uri[]{Uri.parse(ds)};
                else if (data.getClipData() != null)
                    results = new Uri[]{data.getClipData().getItemAt(0).getUri()};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override protected void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onPause()   { super.onPause();   webView.onPause(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        webView.destroy();
    }
}
