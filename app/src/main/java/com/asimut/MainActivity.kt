// MainActivity.kt

package com.asimut

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.asimut.web.WebViewHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var cardButton: FloatingActionButton
    private lateinit var uploadResultProxy: ValueCallback<Array<Uri>>

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val resultUris: Array<Uri>? = when {
            result.resultCode != RESULT_OK -> null
            data == null -> null
            data.data != null -> arrayOf(data.data!!)
            data.clipData != null -> {
                val cd: ClipData = data.clipData!!
                Array(cd.itemCount) { i -> cd.getItemAt(i).uri }
            }
            else -> null
        }
        uploadResultProxy.onReceiveValue(resultUris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setTheme(R.style.Theme_Asimut_API23)
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)

        val setup = WebViewHelper.setupWebView(
            activity = this,
            webView = webView,
            openDocumentLauncher = openDocumentLauncher
        ) { proxy ->
            // храним прокси, чтобы вернуть результат выбора файла в onActivityResult API
            uploadResultProxy = proxy!!
        }

        // применяем клиентов
        webView.webChromeClient = setup.chromeClient
        webView.webViewClient = setup.client
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

        setupSocialLinks()

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_asimut -> webView.loadUrl("https://hmtm-hannover.asimut.net/")
                R.id.nav_lms -> webView.loadUrl("https://lms.hmtm-hannover.de/")
                R.id.nav_qis -> webView.loadUrl("https://qis.hmt.hispro.de/")
                R.id.nav_studmail -> webView.loadUrl("https://stud.hmtm-hannover.de/")
                R.id.nav_hmtmh -> webView.loadUrl("https://www.hmtm-hannover.de/")
                R.id.nav_papercut -> webView.loadUrl("https://papercut.hmtm-hannover.de")
                R.id.nav_service_desk -> webView.loadUrl("https://service.hmtm-hannover.de/otobo/customer.pl?Action=CustomerTicketMessage;ServiceID=57;TypeID=7")
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

    private fun setupSocialLinks() {
        val socialLinks = listOf(
            R.id.social_telegram to "https://t.me/mr_mcsnow",
            R.id.social_instagram to "https://www.instagram.com/mr.mcsnow",
            R.id.social_twitter to "https://x.com/mr_mcsnow",
            R.id.social_facebook to "https://www.facebook.com/mr.mcsnow",
            R.id.social_vk to "https://vk.com/s0007",
            R.id.social_whatsapp to "https://wa.me/message/7777RWHOEUXAD1",
            R.id.social_viber to "viber://chat/?number=+4915122084363"
        )

        socialLinks.forEach { (viewId, url) ->
            navView.findViewById<View>(viewId)?.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
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
