package com.asimut.web

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.URLUtil
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.getSystemService

object WebViewHelper {

    data class SetupResult(
        val chromeClient: WebChromeClient,
        val client: WebViewClient
    )

    /** Общая настройка WebView: масштаб/зум, загрузки, автозаполнение login/password, file chooser */
    fun setupWebView(
        activity: Activity,
        webView: WebView,
        openDocumentLauncher: ActivityResultLauncher<Intent>,
        onReceiveUploadResult: (ValueCallback<Array<Uri>>?) -> Unit
    ): SetupResult {

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.setInitialScale(0)
        webView.scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY

        // ---------- Download (скачивание через DownloadManager)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val dm = activity.getSystemService<DownloadManager>() ?: return@setDownloadListener
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent ?: "")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            dm.enqueue(request)
        }

        // ---------- Upload (file chooser)
        var filePathCallback: ValueCallback<Array<Uri>>? = null

        val chromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePath: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Закрыть предыдущий 콜бэк, если был
                filePathCallback?.onReceiveValue(null)
                filePathCallback = filePath

                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                    type = "*/*"
                }

                return try {
                    openDocumentLauncher.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    false
                }
            }
        }

        val client = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                triedSaveOnThisPage = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Автоподстановка: применять ко всем поддержанным сайтам
                autofillGenericCredentials(view)
                // Попытка один раз прочитать введённые пользователем данные и сохранить
                maybeCaptureAndSaveCredentials(view)
            }
        }

        // Передаём обработчик результата выбора файла в Activity
        onReceiveUploadResult.invoke(object : ValueCallback<Array<Uri>> {
            override fun onReceiveValue(value: Array<Uri>?) {
                filePathCallback?.onReceiveValue(value)
                filePathCallback = null
            }
        })

        return SetupResult(chromeClient, client)
    }

    // -------- Автоподстановка и сохранение (универсальный вариант для Asimut/LMS/QIS/и т.д.)

    private fun jsEscape(s: String) = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")

    private fun autofillGenericCredentials(webView: WebView) {
        val prefs = webView.context.getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        val u = prefs.getString("username", null) ?: return
        val p = prefs.getString("password", null) ?: return
        val uEsc = jsEscape(u)
        val pEsc = jsEscape(p)

        val js = """
            (function(){
              var uEl = document.querySelector(
                'input[name="username"],input#username,input[name="user"],input[id*="user"],input[placeholder*="HMTMH"],input[type="email"],input[type="text"]'
              );
              var pEl = document.querySelector(
                'input[type="password"],input#password,input[name="password"],input[id*="pass"]'
              );
              if (uEl) { uEl.value = '$uEsc'; uEl.dispatchEvent(new Event('input',{bubbles:true})); }
              if (pEl) { pEl.value = '$pEsc'; pEl.dispatchEvent(new Event('input',{bubbles:true})); }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private var triedSaveOnThisPage = false

    private fun maybeCaptureAndSaveCredentials(webView: WebView) {
        if (triedSaveOnThisPage) return
        triedSaveOnThisPage = true

        val js = """
            (function(){
              function val(sel){
                var el = document.querySelector(sel);
                return el ? el.value : '';
              }
              var u = val('input[name="username"],input#username,input[name="user"],input[id*="user"],input[placeholder*="HMTMH"],input[type="email"],input[type="text"]');
              var p = val('input[type="password"],input#password,input[name="password"],input[id*="pass"]');
              if(u && p){ return u + '::SEP::' + p; }
              return '';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            if (result.isNullOrBlank() || result == "null" || result == "\"\"") return@evaluateJavascript
            val decoded = result.trim('"')
            val parts = decoded.split("::SEP::")
            if (parts.size == 2) {
                val (u, p) = parts
                if (u.isNotBlank() && p.isNotBlank()) {
                    webView.context
                        .getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
                        .edit()
                        .putString("username", u)
                        .putString("password", p)
                        .apply()
                }
            }
        }
    }
}
