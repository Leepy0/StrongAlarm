@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leepy0.strongalarm.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import io.github.leepy0.strongalarm.R
import io.github.leepy0.strongalarm.alarm.AlarmScheduler
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.lights.SmartThingsAuth
import io.github.leepy0.strongalarm.ui.components.AppIcon
import io.github.leepy0.strongalarm.ui.theme.AppTheme
import io.github.leepy0.strongalarm.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SmartThings OAuth 로그인. redirect URI로 이동하는 순간 가로채 code를 토큰으로 교환.
 * 페이지 로딩 진행률, 토큰 교환 중 표시, 실패 시 원인과 해결법을 화면에 보여줌
 */
class SmartThingsLoginActivity : ComponentActivity() {
    private var handled = false
    private lateinit var redirectUri: String
    private lateinit var authorizeUrl: String
    private var web: WebView? = null

    /** 페이지 로딩 0~100. 100이면 숨김 */
    private var pageProgress by mutableFloatStateOf(0f)
    private var finishing by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val cfg = Stores.settings.get(this).smartThings
        redirectUri = cfg.redirectUri
        authorizeUrl = SmartThingsAuth.authorizeUrl(cfg.clientId, cfg.redirectUri)

        setContent {
            AppTheme {
                Column(Modifier.fillMaxSize().background(Palette.Night)) {
                    TopAppBar(
                        title = { Text("SmartThings 로그인", style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { IconButton(onClick = ::finish) { AppIcon(R.drawable.ic_back, Palette.Ink) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Night, titleContentColor = Palette.Ink),
                    )
                    // 로딩 진행률 (확정형). 끝나면 같은 높이를 비워 화면이 튀지 않게
                    Box(Modifier.fillMaxWidth().height(4.dp)) {
                        if (pageProgress < 1f && error == null && !finishing) {
                            LinearProgressIndicator(
                                progress = { pageProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Palette.SunText,
                                trackColor = Palette.Line,
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
                        // 화면에서 떼어낸 뒤 해제 (붙은 채로 destroy하면 일부 WebView에서 크래시)
                        AndroidView(
                            factory = { createWebView() },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { view ->
                                view.stopLoading()
                                view.destroy()
                                if (web === view) web = null
                            },
                        )
                        when {
                            error != null -> ErrorPanel(error!!)
                            finishing -> Finishing()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                pageProgress = newProgress / 100f
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handle(request.url)

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (handle(Uri.parse(url))) view.stopLoading()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                // 본문 페이지 로딩 실패만 표시 (이미지 등 하위 리소스 실패는 무시)
                if (request.isForMainFrame && !handled) {
                    error = "로그인 페이지를 열지 못했어요. 인터넷 연결을 확인해주세요."
                }
            }
        }
        loadUrl(authorizeUrl)
    }.also { web = it }

    @Composable
    private fun Finishing() {
        Column(
            Modifier.fillMaxSize().background(Palette.Night),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.size(32.dp), color = Palette.SunText, strokeWidth = 3.dp)
            Text(
                "로그인 마무리 중",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Ink,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    @Composable
    private fun ErrorPanel(message: String) {
        Column(
            Modifier.fillMaxSize().background(Palette.Night).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("로그인하지 못했어요", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = Palette.Mist, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = ::retry,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Sun, contentColor = Palette.SunInk),
                ) { Text("다시 시도") }
                TextButton(onClick = ::finish) { Text("연결 정보 고치기", color = Palette.Ink) }
            }
        }
    }

    private fun retry() {
        handled = false
        error = null
        finishing = false
        pageProgress = 0f
        web?.loadUrl(authorizeUrl)
    }

    private fun handle(uri: Uri): Boolean {
        if (!uri.toString().startsWith(redirectUri)) return false
        if (handled) return true
        handled = true
        val code = uri.getQueryParameter("code")
        val appCtx = applicationContext
        if (code == null) {
            // 사용자가 거부했거나 앱 설정 문제로 code 없이 돌아옴
            val reason = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
            error = "권한 허용 단계에서 취소됐어요." + (reason?.let { " ($it)" } ?: "")
            lifecycleScope.launch(Dispatchers.IO) { HistoryLog.add(appCtx, "SmartThings 로그인 취소: ${reason ?: "code 없음"}") }
            return true
        }
        finishing = true
        lifecycleScope.launch {
            val ok = SmartThingsAuth.exchangeCode(appCtx, code)
            withContext(Dispatchers.IO) {
                HistoryLog.add(appCtx, if (ok) "SmartThings 로그인" else "SmartThings 토큰 교환 실패")
                if (ok) AlarmScheduler.rescheduleAll(appCtx)
            }
            if (ok) {
                Toast.makeText(appCtx, "SmartThings 연결됨", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                finishing = false
                error = "토큰을 받지 못했어요. Client ID, Client Secret, Redirect URI가 CLI로 OAuth 앱을 만들 때 등록한 값과 같은지 확인해주세요."
            }
        }
        return true
    }

    override fun onDestroy() {
        // WebView 해제는 AndroidView onRelease에서 (Compose가 뷰를 떼어낸 뒤)
        web = null
        super.onDestroy()
    }
}
