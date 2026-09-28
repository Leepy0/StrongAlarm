package io.github.leepy0.strongalarm.lights

import android.content.Context
import android.util.Log
import io.github.leepy0.strongalarm.data.SecureStore
import io.github.leepy0.strongalarm.data.Storage
import io.github.leepy0.strongalarm.data.Stores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

/**
 * SmartThings OAuth (API Access 앱, authorization_code).
 * access token 24시간, refresh token은 29일 미사용 시 만료 → 매일 알람에서 사용되므로 유지됨
 */
object SmartThingsAuth {
    private const val TAG = "SmartThingsAuth"
    private const val FILE = "st_secrets.bin"
    private const val AUTHORIZE = "https://api.smartthings.com/oauth/authorize"
    private const val TOKEN = "https://api.smartthings.com/oauth/token"
    const val SCOPE = "r:devices:* x:devices:*"

    @Serializable
    data class Secrets(
        val clientSecret: String = "",
        val accessToken: String = "",
        val refreshToken: String = "",
        val expiresAt: Long = 0,
    )

    private val mutex = Mutex()

    private fun load(ctx: Context): Secrets =
        SecureStore.read(ctx, FILE)?.let {
            runCatching { Storage.json.decodeFromString(Secrets.serializer(), it) }.getOrNull()
        } ?: Secrets()

    private fun save(ctx: Context, s: Secrets) =
        SecureStore.write(ctx, FILE, Storage.json.encodeToString(Secrets.serializer(), s))

    fun hasClientSecret(ctx: Context) = load(ctx).clientSecret.isNotEmpty()

    fun isLoggedIn(ctx: Context) = load(ctx).refreshToken.isNotEmpty()

    fun saveClientSecret(ctx: Context, secret: String) = save(ctx, load(ctx).copy(clientSecret = secret.trim()))

    fun logout(ctx: Context) = save(ctx, load(ctx).copy(accessToken = "", refreshToken = "", expiresAt = 0))

    fun authorizeUrl(clientId: String, redirectUri: String): String =
        "$AUTHORIZE?client_id=${enc(clientId)}&response_type=code" +
            "&redirect_uri=${enc(redirectUri)}&scope=${enc(SCOPE)}"

    suspend fun exchangeCode(ctx: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cfg = Stores.settings.get(ctx).smartThings
            val body = form(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to cfg.redirectUri,
                "client_id" to cfg.clientId,
            )
            tokenRequest(ctx, body)
        }
    }

    /** 유효한 access token. 만료 5분 전이면 갱신 */
    suspend fun accessToken(ctx: Context): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val s = load(ctx)
            if (s.refreshToken.isEmpty()) return@withLock null
            if (s.accessToken.isNotEmpty() && s.expiresAt - System.currentTimeMillis() > 5 * 60_000L) {
                return@withLock s.accessToken
            }
            val cfg = Stores.settings.get(ctx).smartThings
            val body = form(
                "grant_type" to "refresh_token",
                "refresh_token" to s.refreshToken,
                "client_id" to cfg.clientId,
            )
            if (tokenRequest(ctx, body)) load(ctx).accessToken else null
        }
    }

    private fun tokenRequest(ctx: Context, body: String): Boolean {
        val cfg = Stores.settings.get(ctx).smartThings
        val s = load(ctx)
        if (cfg.clientId.isEmpty() || s.clientSecret.isEmpty()) return false
        val basic = Base64.getEncoder().encodeToString("${cfg.clientId}:${s.clientSecret}".toByteArray())
        val res = Http.request(
            "POST", TOKEN,
            headers = mapOf("Authorization" to "Basic $basic"),
            body = body,
            contentType = "application/x-www-form-urlencoded",
        )
        if (!res.ok) {
            Log.e(TAG, "토큰 요청 실패 ${res.code}: ${res.body}")
            return false
        }
        return runCatching {
            val o = JSONObject(res.body)
            save(
                ctx,
                s.copy(
                    accessToken = o.getString("access_token"),
                    // 갱신 응답에 refresh token이 없으면 기존 값 유지
                    refreshToken = o.optString("refresh_token").ifEmpty { s.refreshToken },
                    expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 86_400) * 1000,
                ),
            )
            true
        }.getOrElse {
            Log.e(TAG, "토큰 응답 파싱 실패", it)
            false
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun form(vararg pairs: Pair<String, String>) =
        pairs.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
}
