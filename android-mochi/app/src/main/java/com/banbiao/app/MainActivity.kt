package com.banbiao.app

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.banbiao.app.data.AppState
import com.banbiao.app.data.StateStore
import com.banbiao.app.ui.CadenceRoot
import org.json.JSONObject
import ui.feice.mochi.utils.mochiEnableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var store: StateStore
    private var migrationDone = false
    private var migrationAttempts = 0
    private var legacyWebView: WebView? = null
    private var migrationTimeout: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        mochiEnableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = StateStore(this)
        if (store.isMigrated()) {
            showApp(store.load())
        } else {
            migrateLegacy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun migrateLegacy() {
        migrationAttempts++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("legacy_import")
            val source = File(dataDir, "app_webview/Default/Local Storage")
            val target = File(dataDir, "app_webview_legacy_import/Default/Local Storage")
            if (source.exists()) {
                target.deleteRecursively()
                runCatching { copyTree(source, target) }
            }
        }
        val web = WebView(this)
        legacyWebView = web
        web.setBackgroundColor(0xFFF5F5F7.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.addJavascriptInterface(LegacyBridge(), "Legacy")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                return if (request.url.toString().startsWith("https://shift.local/")) {
                    WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        ByteArrayInputStream(LEGACY_HTML.toByteArray(Charsets.UTF_8)),
                    )
                } else {
                    null
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(
                    "Legacy.onState(JSON.stringify({state:localStorage.getItem('banbiao.schedule.v1')||'',theme:localStorage.getItem('banbiao.theme')||''}))",
                    null,
                )
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail,
            ): Boolean {
                onMigrationFailure()
                return true
            }
        }
        setContentView(web)
        migrationTimeout?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        val timeout = Runnable { onMigrationFailure() }
        migrationTimeout = timeout
        Handler(Looper.getMainLooper()).postDelayed(timeout, 6000)
        web.loadUrl("https://shift.local/migrate")
    }

    private fun copyTree(source: File, target: File) {
        if (source.isDirectory) {
            if (!target.exists()) target.mkdirs()
            source.listFiles()?.forEach { child ->
                if (child.name != "LOCK" && child.name != "LOG" && child.name != "SingletonLock") {
                    copyTree(child, File(target, child.name))
                }
            }
        } else if (source.isFile) {
            source.copyTo(target, overwrite = true)
        }
    }

    private fun onMigrationFailure() {
        if (migrationDone) return
        migrationTimeout?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        migrationTimeout = null
        legacyWebView?.let { web ->
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        legacyWebView = null
        if (migrationAttempts < 2) {
            Handler(Looper.getMainLooper()).postDelayed({ migrateLegacy() }, 400)
        } else {
            showMigrationFallback()
        }
    }

    private fun showMigrationFallback() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "旧版排班数据读取失败\n可以重试，或跳过导入。"
            textSize = 16f
            gravity = Gravity.CENTER
        })
        root.addView(Button(this).apply {
            text = "重试导入"
            setOnClickListener {
                migrationAttempts = 0
                migrateLegacy()
            }
        })
        root.addView(Button(this).apply {
            text = "跳过导入"
            setOnClickListener { finishMigration(null, null) }
        })
        setContentView(root)
    }

    private inner class LegacyBridge {
        @JavascriptInterface
        fun onState(raw: String) {
            runOnUiThread {
                runCatching {
                    val obj = JSONObject(raw)
                    finishMigration(obj.optString("state"), obj.optString("theme"))
                }.onFailure {
                    finishMigration(null, null)
                }
            }
        }
    }

    private fun finishMigration(legacyState: String?, legacyTheme: String?) {
        if (migrationDone) return
        migrationDone = true
        migrationTimeout?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        migrationTimeout = null
        val web = legacyWebView
        legacyWebView = null
        if (web != null) {
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            web.destroy()
        }
        val state: AppState = if (legacyState.isNullOrBlank() && legacyTheme.isNullOrBlank()) {
            store.markMigrated()
            store.load()
        } else {
            store.importLegacy(legacyState, legacyTheme)
        }
        showApp(state)
    }

    private fun showApp(state: AppState) {
        setContent { CadenceRoot(initialState = state) }
    }

    override fun onDestroy() {
        migrationTimeout?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        legacyWebView?.destroy()
        legacyWebView = null
        super.onDestroy()
    }

    companion object {
        private const val LEGACY_HTML = """<!doctype html><html><body><script>
window.addEventListener('load', function () {
  Legacy.onState(JSON.stringify({
    state: localStorage.getItem('banbiao.schedule.v1') || '',
    theme: localStorage.getItem('banbiao.theme') || ''
  }));
});
</script></body></html>"""
    }
}
