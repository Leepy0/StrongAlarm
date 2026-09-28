package io.github.leepy0.strongalarm.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.leepy0.strongalarm.alarm.AlarmScheduler
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.lights.SmartThingsAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** SmartThings OAuth 로그인. redirect URI로 이동하는 순간 가로채 code를 토큰으로 교환 */
class SmartThingsLoginActivity : ComponentActivity() {
    private var handled = false
    private lateinit var redirectUri: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = Stores.settings.get(this).smartThings
        redirectUri = cfg.redirectUri
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    handle(request.url)

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (handle(Uri.parse(url))) view.stopLoading()
                }
            }
        }
        setContentView(web)
        web.loadUrl(SmartThingsAuth.authorizeUrl(cfg.clientId, cfg.redirectUri))
    }

    private fun handle(uri: Uri): Boolean {
        if (!uri.toString().startsWith(redirectUri)) return false
        if (handled) return true
        handled = true
        val code = uri.getQueryParameter("code")
        val appCtx = applicationContext
        lifecycleScope.launch {
            val ok = code != null && SmartThingsAuth.exchangeCode(appCtx, code)
            Toast.makeText(appCtx, if (ok) "SmartThings 연결됨" else "로그인 실패", Toast.LENGTH_SHORT).show()
            withContext(Dispatchers.IO) {
                HistoryLog.add(appCtx, if (ok) "SmartThings 로그인" else "SmartThings 로그인 실패")
                AlarmScheduler.rescheduleAll(appCtx)
            }
            finish()
        }
        return true
    }
}
