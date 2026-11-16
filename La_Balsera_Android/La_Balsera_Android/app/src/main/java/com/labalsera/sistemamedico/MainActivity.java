package com.labalsera.sistemamedico;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * La Balsera - Sistema Médico Pro (Android).
 * Muestra la app incluida dentro del APK (sin internet) y conecta con el HTML:
 *   AndroidApp.guardarArchivo(nombre, mime, base64)  -> guarda respaldos en Descargas
 *   AndroidApp.imprimir(titulo)                       -> imprimir / guardar como PDF
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String INICIO = "https://" + HOST + "/assets/index.html";
    private static final int PEDIR_ARCHIVO = 1001;

    private WebView web;
    private ValueCallback<Uri[]> selectorArchivo;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        getWindow().setStatusBarColor(Color.parseColor("#047857"));

        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader cargador = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // localStorage
        s.setDatabaseEnabled(true);        // IndexedDB
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return cargador.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) { /* sin app para abrirlo */ }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (selectorArchivo != null) selectorArchivo.onReceiveValue(null);
                selectorArchivo = cb;
                try {
                    startActivityForResult(p.createIntent(), PEDIR_ARCHIVO);
                } catch (Exception e) {
                    selectorArchivo = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Puente(), "AndroidApp");
        web.loadUrl(INICIO);
    }

    @Override
    protected void onActivityResult(int codigo, int resultado, Intent datos) {
        if (codigo == PEDIR_ARCHIVO) {
            if (selectorArchivo != null) {
                selectorArchivo.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultado, datos));
                selectorArchivo = null;
            }
            return;
        }
        super.onActivityResult(codigo, resultado, datos);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void avisar(final String texto) {
        runOnUiThread(new Runnable() {
            @Override public void run() { Toast.makeText(MainActivity.this, texto, Toast.LENGTH_LONG).show(); }
        });
    }

    /** Funciones que la app (HTML) puede llamar. */
    private class Puente {

        @JavascriptInterface
        public void guardarArchivo(String nombre, String mime, String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                OutputStream salida;
                String donde;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    salida = getContentResolver().openOutputStream(uri);
                    donde = "la carpeta Descargas";
                } else {
                    File carpeta = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    carpeta.mkdirs();
                    salida = new FileOutputStream(new File(carpeta, nombre));
                    donde = carpeta.getAbsolutePath();
                }
                salida.write(bytes);
                salida.close();
                avisar("Guardado en " + donde + ": " + nombre);
            } catch (Exception e) {
                avisar("No se pudo guardar el archivo");
            }
        }

        @JavascriptInterface
        public void imprimir(final String titulo) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        PrintDocumentAdapter adaptador = web.createPrintDocumentAdapter(titulo);
                        pm.print(titulo, adaptador, new PrintAttributes.Builder().build());
                    } catch (Exception e) {
                        avisar("No se pudo abrir la impresión");
                    }
                }
            });
        }
    }
}
