package io.github.leepy0.strongalarm.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.leepy0.strongalarm.data.Storage
import io.github.leepy0.strongalarm.data.Stores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL

/**
 * 새 버전 확인 (GitHub 'latest' 릴리스의 version.json, 공개 레포라 인증 불필요).
 * 설치는 앱이 직접 하지 않고, 브라우저로 APK를 받아 시스템 설치 화면에서 진행
 */
object Updater {
    private const val TAG = "Updater"
    private const val BASE = "https://github.com/Leepy0/StrongAlarm/releases/download/latest"
    private const val VERSION_URL = "$BASE/version.json"
    const val PHONE_APK_URL = "$BASE/StrongAlarm-phone.apk"
    const val WATCH_APK_URL = "$BASE/StrongAlarm-watch.apk"

    @Serializable
    data class Note(val v: Int, val s: String)

    @Serializable
    data class Remote(
        val versionCode: Int,
        val versionName: String,
        val sha: String = "",
        val phoneSha256: String = "",
        val phoneSize: Long = 0,
        val watchVersionCode: Int = 0,
        val notes: List<Note> = emptyList(),
    )

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val remote: Remote) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun installedVersionCode(ctx: Context): Long =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    fun installedVersionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /**
     * 새 버전 확인. 새 버전이 있으면 Remote, 없거나 실패하면 null
     * @param silent true면 실패해도 Failed로 표시하지 않음 (자동 확인)
     */
    suspend fun check(ctx: Context, silent: Boolean = true): Remote? = withContext(Dispatchers.IO) {
        if (_state.value == State.Checking) return@withContext null
        val before = _state.value
        _state.value = State.Checking
        val remote = runCatching {
            Storage.json.decodeFromString(Remote.serializer(), httpGetText(VERSION_URL))
        }.onFailure { Log.w(TAG, "버전 확인 실패", it) }.getOrNull()

        Stores.state.update(ctx) { it.copy(updateCheckedAt = System.currentTimeMillis()) }
        when {
            remote == null -> {
                _state.value = if (silent) before.takeIf { it !is State.Checking } ?: State.Idle
                else State.Failed("버전 정보를 못 받았어요. 인터넷 연결을 확인해주세요.")
                null
            }
            remote.versionCode > installedVersionCode(ctx) -> {
                _state.value = State.Available(remote)
                remote
            }
            else -> {
                _state.value = State.UpToDate
                null
            }
        }
    }

    /** 이 버전 이후 변경 내역 */
    fun notesSince(ctx: Context, remote: Remote): List<Note> {
        val cur = installedVersionCode(ctx)
        return remote.notes.filter { it.v > cur }
    }

    /** 브라우저로 APK 다운로드 (다운로드 후 알림을 눌러 시스템 설치 화면에서 설치) */
    fun downloadIntent(url: String = PHONE_APK_URL): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun httpGetText(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) error("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
