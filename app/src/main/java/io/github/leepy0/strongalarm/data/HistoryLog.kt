package io.github.leepy0.strongalarm.data

import android.content.Context
import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 판정·울림·해제 이력. 최근 200줄 유지 */
object HistoryLog {
    private const val FILE = "history.log"
    private const val MAX_LINES = 200
    private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    private val lock = Any()

    fun add(ctx: Context, message: String) {
        Log.i("HistoryLog", message)
        synchronized(lock) {
            val line = "${LocalDateTime.now().format(fmt)} | $message"
            val lines = (read(ctx) + line).takeLast(MAX_LINES)
            Storage.writeText(ctx, FILE, lines.joinToString("\n"))
        }
    }

    fun read(ctx: Context): List<String> =
        Storage.readText(ctx, FILE)?.lines()?.filter { it.isNotBlank() } ?: emptyList()
}
