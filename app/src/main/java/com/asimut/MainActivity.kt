// MainActivity.kt

package com.asimut

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.content.SharedPreferences
import android.webkit.JavascriptInterface
import android.view.animation.AccelerateDecelerateInterpolator
import com.google.android.material.floatingactionbutton.FloatingActionButton

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
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
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