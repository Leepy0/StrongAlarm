package io.github.leepy0.strongalarm.lights

import java.net.HttpURLConnection
import java.net.URL

internal data class HttpResult(val code: Int, val body: String) {
    val ok get() = code in 200..299
}

internal object Http {
    /** 블로킹 호출. IO 디스패처에서 사용 */
    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        contentType: String = "application/json",
    ): HttpResult {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = method
            c.connectTimeout = 6_000
            c.readTimeout = 8_000
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType)
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            HttpResult(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: Exception) {
            HttpResult(-1, e.toString())
        } finally {
            c.disconnect()
        }
    }
}
