// MainActivity.kt

package com.asimut

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var cardButton: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setTheme(R.style.Theme_Asimut_API23)
        }

        webView = findViewById(R.id.webview)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
        }
        webView.setInitialScale(0)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                triedSaveThisPage = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.contains("asimut.net", ignoreCase = true)) {
                    autofillAsimutCredentials()
                    // autoSubmitIfBothFieldsFilled()
                    maybeCaptureAndSaveCredentials()
                }
            }
        }
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        val menuButton: ImageButton = findViewById(R.id.menu_button)
        menuButton.setOnClickListener {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START)
                drawerLayout.animate()
                    .translationX(0f)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_asimut -> webView.loadUrl("https://hmtm-hannover.asimut.net/")
                R.id.nav_lms -> webView.loadUrl("https://lms.hmtm-hannover.de/")
                R.id.nav_qis -> webView.loadUrl("https://qis.hmt.hispro.de/")
                R.id.nav_studmail -> webView.loadUrl("https://stud.hmtm-hannover.de/")
                R.id.nav_hmtmh -> webView.loadUrl("https://www.hmtm-hannover.de/")
                R.id.nav_papercut -> webView.loadUrl("https://papercut.hmtm-hannover.de")
                R.id.nav_clear_credentials -> showClearCredentialsDialog()
            }
            drawerLayout.closeDrawers()
            true
        }

        cardButton = findViewById(R.id.card_button)
        cardButton.setOnClickListener {
            val intent = Intent(this, CardManagementActivity::class.java)
            startActivity(intent)
        }

        webView.loadUrl("https://hmtm-hannover.asimut.net/")
    }

    private fun jsEscape(s: String) = s.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")

    private fun autofillAsimutCredentials() {
        val prefs = getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        val username = prefs.getString("username", null)
        val password = prefs.getString("password", null)
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) return

        val u = jsEscape(username)
        val p = jsEscape(password)

        val js = """
            (function(){
              var uEl = document.querySelector(
                'input[name="username"],input#username,input[name="user"],input[id*="user"],input[placeholder*="HMTMH"],input[type="text"]'
              );
              var pEl = document.querySelector(
                'input[type="password"],input#password,input[name="password"],input[id*="pass"]'
              );
              if (uEl) { uEl.value = '$u'; uEl.dispatchEvent(new Event('input',{bubbles:true})); }
              if (pEl) { pEl.value = '$p'; pEl.dispatchEvent(new Event('input',{bubbles:true})); }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private var triedSaveThisPage = false

    private fun maybeCaptureAndSaveCredentials() {
        if (triedSaveThisPage) return
        val js = """
            (function(){
              function val(sel){
                var el = document.querySelector(sel);
                return el ? el.value : '';
              }
              var u = val('input[name="username"],input#username,input[name="user"],input[id*="user"],input[placeholder*="HMTMH"],input[type="text"]');
              var p = val('input[type="password"],input#password,input[name="password"],input[id*="pass"]');
              if(u && p){ return u + '::SEP::' + p; }
              return '';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            if (result.isNullOrBlank() || result == "null" || result == "\"\"") return@evaluateJavascript
            val decoded = result.removePrefix("\"").removeSuffix("\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\")
            val parts = decoded.split("::SEP::")
            if (parts.size == 2) {
                val (u, p) = parts
                if (u.isNotBlank() && p.isNotBlank()) {
                    getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
                        .edit()
                        .putString("username", u)
                        .putString("password", p)
                        .apply()
                    triedSaveThisPage = true
                }
            }
        }
    }

    private fun autoSubmitIfBothFieldsFilled() {
        val js = """
            (function(){
              var uEl = document.querySelector('input[type="text"],input[name="username"],input#username,input[name="user"],input[id*="user"]');
              var pEl = document.querySelector('input[type="password"],input#password,input[name="password"],input[id*="pass"]');
              if(!uEl || !pEl || !uEl.value || !pEl.value) return;
              var btn = document.querySelector('button[type="submit"],input[type="submit"],button[name="login"],button#login');
              if(btn){ btn.click(); }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun showClearCredentialsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Die Anmeldedaten löschen?")
            .setPositiveButton("ja") { dialog, _ ->
                clearCredentials()
                dialog.dismiss()
                finish()
            }
            .setNegativeButton("nein") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun clearCredentials() {
        val sharedPreferences = getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("username")
        editor.remove("password")
        editor.apply()
    }

    private class WebAppInterface(private val context: Context) {
        private val sharedPreferences: SharedPreferences =
            context.getSharedPreferences("user_credentials", Context.MODE_PRIVATE)

        @JavascriptInterface
        fun getUsername(): String? {
            return sharedPreferences.getString("username", null)
        }

        @JavascriptInterface
        fun getPassword(): String? {
            return sharedPreferences.getString("password", null)
        }

        @JavascriptInterface
        fun saveCredentials(username: String, password: String) {
            val editor = sharedPreferences.edit()
            editor.putString("username", username)
            editor.putString("password", password)
            editor.apply()
        }
    }
}