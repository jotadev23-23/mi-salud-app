package com.misalud.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
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
    private long downloadId = -1;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != downloadId) return;
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) return;
            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(downloadId);
            Cursor cursor = dm.query(q);
            if (cursor != null && cursor.moveToFirst()) {
                int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int uriCol    = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                int status    = cursor.getInt(statusCol);
                String uri    = cursor.getString(uriCol);
                cursor.close();
                if (status == DownloadManager.STATUS_SUCCESSFUL && uri != null) {
                    final String path = uri.replace("file://", "");
                    new Handler(Looper.getMainLooper()).post(() ->
                        webView.evaluateJavascript("onApkDescargado('" + path + "')", null));
                } else {
                    new Handler(Looper.getMainLooper()).post(() ->
                        webView.evaluateJavascript("onApkError('Descarga fallida')", null));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AlarmReceiver.createChannels(this);
        registerReceiver(downloadReceiver,
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        webView = findViewById(R.id.webview);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setGeolocationEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);

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
                    GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
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
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"application/json", "text/plain", "*/*"});
                    startActivityForResult(
                        Intent.createChooser(i, "Seleccionar respaldo .json"),
                        FILE_CHOOSER_REQUEST);
                } else {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("image/*");
                    Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    Intent chooser = Intent.createChooser(i, "Seleccionar imagen");
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
    public class AndroidBridge {

        @JavascriptInterface
        public void descargarAPK(final String apkUrl) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (!getPackageManager().canRequestPackageInstalls()) {
                            startActivityForResult(
                                new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())),
                                INSTALL_PERMISSION);
                            webView.evaluateJavascript(
                                "onApkError('Habilitá instalacion de fuentes desconocidas y volvé a intentar')", null);
                            return;
                        }
                    }
                    File apkFile = new File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), "MiSalud-update.apk");
                    if (apkFile.exists()) apkFile.delete();

                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm == null) return;
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
                    req.setTitle("Mi Salud — Descargando actualización");
                    req.setDescription("Nueva versión disponible");
                    req.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE);
                    req.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, "MiSalud-update.apk");
                    req.setMimeType("application/vnd.android.package-archive");
                    downloadId = dm.enqueue(req);

                    // Poll progress
                    final DownloadManager dmPoll = dm;
                    new Thread(() -> {
                        boolean running = true;
                        while (running) {
                            DownloadManager.Query q = new DownloadManager.Query();
                            q.setFilterById(downloadId);
                            Cursor c = dmPoll.query(q);
                            if (c != null && c.moveToFirst()) {
                                int st  = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                                long dl = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                                long tt = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                                c.close();
                                if (tt > 0) {
                                    final int pct = (int)((dl * 100) / tt);
                                    new Handler(Looper.getMainLooper()).post(() ->
                                        webView.evaluateJavascript("onApkProgreso(" + pct + ")", null));
                                }
                                if (st == DownloadManager.STATUS_SUCCESSFUL ||
                                    st == DownloadManager.STATUS_FAILED) running = false;
                            } else running = false;
                            try { Thread.sleep(600); } catch (Exception e) { break; }
                        }
                    }).start();

                } catch (Exception e) {
                    webView.evaluateJavascript(
                        "onApkError('" + e.getMessage().replace("'","") + "')", null);
                }
            });
        }

        @JavascriptInterface
        public void instalarAPK(String apkPath) {
            runOnUiThread(() -> {
                try {
                    File apkFile = new File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), "MiSalud-update.apk");
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
                        "onApkError('" + e.getMessage().replace("'","") + "')", null);
                }
            });
        }

        @JavascriptInterface
        public void sincronizarTodo(String meds, String turnos,
                                    String consultas, String vacunas) {
            AlarmScheduler.cancelAll(getApplicationContext());
            AlarmScheduler.saveMedicamentos(getApplicationContext(), meds);
            AlarmScheduler.saveTurnos(getApplicationContext(), turnos);
            AlarmScheduler.saveConsultas(getApplicationContext(), consultas);
            AlarmScheduler.saveVacunas(getApplicationContext(), vacunas);
            AlarmScheduler.scheduleAll(getApplicationContext());
        }

        @JavascriptInterface
        public void actualizarAlarmas(String json) {
            AlarmScheduler.saveMedicamentos(getApplicationContext(), json);
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
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { AlarmReceiver.stopSound(); }
            }, 3000);
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
        public String guardarRespaldo(String json, String nombre) {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, nombre);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(json.getBytes("UTF-8"));
                fos.close();
                return file.getAbsolutePath();
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
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
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms,
                    VibrationEffect.DEFAULT_AMPLITUDE));
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
        public String getVersion() { return "9.0"; }
    }

    private void requestAppPermissions() {
        List<String> needed = new ArrayList<>();
        String[] perms = {
            Manifest.permission.VIBRATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
        };
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        for (String p : perms) {
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
                if (ds != null) {
                    results = new Uri[]{Uri.parse(ds)};
                } else if (data.getClipData() != null) {
                    results = new Uri[]{data.getClipData().getItemAt(0).getUri()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onPause()   { super.onPause();   webView.onPause(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        webView.destroy();
    }
}
