package io.github.leepy0.strongalarm.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** 모든 파일은 기기 보호 저장소(Direct Boot 가능)에 저장 → 재부팅 후 잠금 해제 전에도 알람 동작 */
object Storage {
    private const val TAG = "Storage"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun dir(ctx: Context): File = ctx.createDeviceProtectedStorageContext().filesDir

    fun readText(ctx: Context, name: String): String? = runCatching {
        AtomicFile(File(dir(ctx), name)).readFully().toString(Charsets.UTF_8)
    }.getOrNull()

    fun writeText(ctx: Context, name: String, text: String) {
        val file = AtomicFile(File(dir(ctx), name))
        val out = runCatching { file.startWrite() }.getOrElse {
            Log.e(TAG, "쓰기 실패: $name", it)
            return
        }
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e(TAG, "쓰기 실패: $name", e)
        }
    }
}

/** JSON 파일 1개 + StateFlow. 프로세스 내 단일 인스턴스 */
class JsonFileStore<T>(
    private val fileName: String,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    private val lock = Any()

    @Volatile
    private var flow: MutableStateFlow<T>? = null

    fun flow(ctx: Context): StateFlow<T> = ensure(ctx)

    fun get(ctx: Context): T = ensure(ctx).value

    fun update(ctx: Context, transform: (T) -> T): T = synchronized(lock) {
        val f = ensure(ctx)
        val next = transform(f.value)
        if (next != f.value) {
            Storage.writeText(ctx, fileName, Storage.json.encodeToString(serializer, next))
            f.value = next
        }
        next
    }

    private fun ensure(ctx: Context): MutableStateFlow<T> =
        flow ?: synchronized(lock) {
            flow ?: MutableStateFlow(load(ctx)).also { flow = it }
        }

    private fun load(ctx: Context): T {
        val text = Storage.readText(ctx, fileName) ?: return default()
        return runCatching { Storage.json.decodeFromString(serializer, text) }.getOrElse { default() }
    }
}
