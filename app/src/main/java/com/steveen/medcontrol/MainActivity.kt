package com.steveen.medcontrol

import android.annotation.SuppressLint
import android.graphics.Color
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var currentRingtone: Ringtone? = null

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor("#F4F7F5"))

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.addJavascriptInterface(RingtoneInterface(), "AndroidRingtone")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = false
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class RingtoneInterface {

        @JavascriptInterface
        fun getAlarmSounds(): String {
            val arr = JSONArray()
            arr.put(JSONObject().apply {
                put("name", "Pitido electrónico (predeterminado)")
                put("uri", "beep")
            })
            try {
                // Use applicationContext so RingtoneManager does NOT register the cursor
                // as a managed cursor on the Activity — avoids StaleDataException on restart.
                val rm = RingtoneManager(applicationContext)
                rm.setType(RingtoneManager.TYPE_ALARM)
                val cursor = rm.cursor
                try {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                        val uri = rm.getRingtoneUri(cursor.position).toString()
                        arr.put(JSONObject().apply {
                            put("name", name)
                            put("uri", uri)
                        })
                    }
                } finally {
                    cursor.close()
                }
            } catch (e: Exception) {
                // Return at least the beep option
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun playSound(uri: String) {
            runOnUiThread {
                try {
                    currentRingtone?.stop()
                    currentRingtone = null
                    if (uri == "beep") return@runOnUiThread
                    val ringtone = RingtoneManager.getRingtone(this@MainActivity, Uri.parse(uri))
                    ringtone?.play()
                    currentRingtone = ringtone
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
        }

        @JavascriptInterface
        fun stopSound() {
            runOnUiThread {
                try {
                    currentRingtone?.stop()
                    currentRingtone = null
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        currentRingtone?.stop()
        currentRingtone = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
