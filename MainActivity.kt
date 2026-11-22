package com.xuper.donador

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val networkExecutor = Executors.newCachedThreadPool()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#0b111a")
        window.navigationBarColor = Color.parseColor("#0b111a")

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0b111a"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.allowFileAccess = true
            settings.databaseEnabled = true

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false
                }
            }
            webChromeClient = WebChromeClient()

            // Puente nativo: todas las peticiones HTTP salen por HttpURLConnection de Android (sin CORS)
            addJavascriptInterface(NativeHttpBridge(this@MainActivity, this), "AndroidNative")
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onDestroy() {
        networkExecutor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }

    inner class NativeHttpBridge(
        private val context: Context,
        private val targetWebView: WebView
    ) {
        @JavascriptInterface
        fun request(
            callbackId: String,
            urlStr: String,
            method: String,
            headersJson: String,
            body: String?
        ) {
            networkExecutor.execute {
                val responsePayload = JSONObject()
                var conn: HttpURLConnection? = null
                try {
                    val url = URL(urlStr)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = method.uppercase()
                        connectTimeout = 15000
                        readTimeout = 20000
                        useCaches = false
                        doInput = true
                        instanceFollowRedirects = true
                    }

                    var hasUserAgent = false
                    if (headersJson.isNotBlank()) {
                        val headersObj = JSONObject(headersJson)
                        val keys = headersObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = headersObj.optString(key, "")
                            if (key.equals("User-Agent", ignoreCase = true)) {
                                hasUserAgent = true
                            }
                            conn.setRequestProperty(key, value)
                        }
                    }

                    // Identificarse como cliente Android nativo (Dalvik), nunca como navegador
                    if (!hasUserAgent) {
                        conn.setRequestProperty(
                            "User-Agent",
                            "Dalvik/2.1.0 (Linux; U; Android 13; sdk_gphone64_arm64 Build/TQ3A.230901.001)"
                        )
                    }

                    if (body != null && (method.equals("POST", true) || method.equals("PUT", true))) {
                        conn.doOutput = true
                        val bytes = body.toByteArray(StandardCharsets.UTF_8)
                        conn.setFixedLengthStreamingMode(bytes.size)
                        conn.outputStream.use { os ->
                            os.write(bytes)
                            os.flush()
                        }
                    }

                    val statusCode = conn.responseCode
                    val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
                    val responseBody = if (stream != null) {
                        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                            reader.readText()
                        }
                    } else {
                        ""
                    }

                    responsePayload.put("ok", statusCode in 200..299)
                    responsePayload.put("status", statusCode)
                    responsePayload.put("body", responseBody)
                    responsePayload.put("error", JSONObject.NULL)
                } catch (e: Exception) {
                    responsePayload.put("ok", false)
                    responsePayload.put("status", 0)
                    responsePayload.put("body", "")
                    responsePayload.put("error", e.message ?: e.javaClass.simpleName)
                } finally {
                    conn?.disconnect()
                }

                val safeCallbackId = JSONObject.quote(callbackId)
                val safeJsonPayload = JSONObject.quote(responsePayload.toString())
                targetWebView.post {
                    targetWebView.evaluateJavascript(
                        "window.__onNativeHttpResponse($safeCallbackId, $safeJsonPayload);",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun copyText(label: String, text: String): Boolean {
            return try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, text)
                clipboard.setPrimaryClip(clip)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
