package io.github.leepy0.strongalarm.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import io.github.leepy0.strongalarm.alarm.Notifications
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Stores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 새 버전 APK를 시스템 DownloadManager로 받음 (브라우저 다운로드가 100%에서 멈추는 문제 회피).
 * 받은 파일은 '다운로드' 폴더에 저장되고, 다운로드 완료 알림이나 시스템 다운로드 목록에서 눌러 설치.
 * 앱은 설치 권한이 없고 직접 설치하지 않음
 */
object ApkDownloads {
    enum class Kind(val file: String, val url: String, val label: String) {
        PHONE("phone", Updater.PHONE_APK_URL, "폰"),
        WATCH("watch", Updater.WATCH_APK_URL, "워치"),
    }

    /** 화면 표시용 진행 상태 */
    data class Progress(
        val id: Long,
        val kind: Kind,
        val versionName: String,
        /** 0~1, 크기를 모르면 null */
        val fraction: Float?,
        val status: Int,
        val reason: String? = null,
    ) {
        val done get() = status == DownloadManager.STATUS_SUCCESSFUL
        val failed get() = status == DownloadManager.STATUS_FAILED
        val active get() = !done && !failed
    }

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    /** 손상으로 지운 다운로드 (진행 화면에 '취소' 대신 정확한 사유 표시) */
    private val corrupted = mutableSetOf<Long>()

    /** updateDownloads 값: "PHONE:sha256". 예전 형식(sha만)은 폰으로 간주 */
    private fun encode(kind: Kind, sha: String) = "${kind.name}:$sha"
    private fun kindOf(v: String) = if (v.startsWith("WATCH:")) Kind.WATCH else Kind.PHONE
    private fun shaOf(v: String) = v.substringAfter(':', v)

    /** 다운로드 시작. 같은 종류가 이미 받는 중이면 그대로 둠, 같은 종류의 이전 파일은 정리 */
    fun start(ctx: Context, kind: Kind, versionName: String, sha256: String) {
        _progress.value?.let { if (it.kind == kind && it.active) return }
        val dm = ctx.getSystemService(DownloadManager::class.java)
        removeKind(ctx, kind)
        val name = "StrongAlarm-${kind.file}-$versionName.apk"
        val req = DownloadManager.Request(Uri.parse(kind.url))
            .setTitle("StrongAlarm ${kind.label} $versionName")
            .setDescription("다 받으면 눌러서 설치")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        val id = dm.enqueue(req)
        // 완료 시 무결성 확인용 sha256 (프로세스가 재시작돼도 리시버가 읽을 수 있게 저장)
        Stores.state.update(ctx) { it.copy(updateDownloads = it.updateDownloads + (id.toString() to encode(kind, sha256))) }
        _progress.value = Progress(id, kind, versionName, 0f, DownloadManager.STATUS_PENDING)
    }

    /** 진행률 갱신 (IO 스레드에서 호출). 받는 중이면 true */
    fun poll(ctx: Context): Boolean {
        val cur = _progress.value ?: return false
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(cur.id)).use { c ->
            if (!c.moveToFirst()) {
                val reason = if (cur.id in corrupted) "받은 파일이 손상돼 지웠어요. 다시 받아주세요." else "다운로드가 취소됐어요"
                _progress.value = cur.copy(status = DownloadManager.STATUS_FAILED, reason = reason)
                return false
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reasonCode = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            _progress.value = cur.copy(
                fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null,
                status = status,
                reason = if (status == DownloadManager.STATUS_FAILED) failReason(reasonCode) else null,
            )
            return status != DownloadManager.STATUS_SUCCESSFUL && status != DownloadManager.STATUS_FAILED
        }
    }

    fun clear() {
        _progress.value = null
    }

    /** 시스템 다운로드 목록 (여기서 APK를 누르면 설치 화면) */
    fun openDownloadsIntent(): Intent =
        Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 폰 업데이트 후 지난 폰 APK 정리 (워치 APK는 Wear Installer로 따로 설치하므로 남김) */
    fun cleanup(ctx: Context) {
        removeKind(ctx, Kind.PHONE)
        if (_progress.value?.kind == Kind.PHONE) _progress.value = null
    }

    private fun removeKind(ctx: Context, kind: Kind) {
        val entries = Stores.state.get(ctx).updateDownloads.filterValues { kindOf(it) == kind }
        val ids = entries.keys.mapNotNull { it.toLongOrNull() }
        if (ids.isNotEmpty()) {
            runCatching { ctx.getSystemService(DownloadManager::class.java).remove(*ids.toLongArray()) }
        }
        Stores.state.update(ctx) { it.copy(updateDownloads = it.updateDownloads - entries.keys) }
    }

    enum class Verify { NOT_OURS, FAILED, OK, CORRUPT }

    /**
     * 다운로드 완료 브로드캐스트 처리. 실패한 다운로드도 이 브로드캐스트가 오므로
     * 성공일 때만 sha256 확인하고, 다르면 파일을 지움
     */
    internal fun verify(ctx: Context, id: Long): Verify {
        val entry = Stores.state.get(ctx).updateDownloads[id.toString()] ?: return Verify.NOT_OURS
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val status = runCatching {
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else null
            }
        }.getOrNull()
        if (status != DownloadManager.STATUS_SUCCESSFUL) return Verify.FAILED
        val expected = shaOf(entry)
        if (expected.isEmpty()) return Verify.OK
        val actual = runCatching {
            dm.openDownloadedFile(id).use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    val md = MessageDigest.getInstance("SHA-256")
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                    }
                    md.digest().joinToString("") { "%02x".format(it) }
                }
            }
        }.getOrNull() ?: return Verify.FAILED
        if (actual == expected) return Verify.OK
        corrupted += id
        dm.remove(id)
        Stores.state.update(ctx) { it.copy(updateDownloads = it.updateDownloads - id.toString()) }
        return Verify.CORRUPT
    }

    private fun failReason(code: Int) = when (code) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "저장 공간이 부족해요"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_CANNOT_RESUME -> "연결이 끊겼어요. 다시 받아주세요."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS, DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "서버 응답 오류예요. 잠시 뒤 다시 받아주세요."
        else -> "받지 못했어요 (코드 $code). 다시 받아주세요."
    }
}

/** DownloadManager 완료 브로드캐스트 → 무결성 확인 (손상 시 삭제·알림). 시스템 다운로드 앱이 보내므로 exported */
class ApkDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                when (ApkDownloads.verify(ctx, id)) {
                    ApkDownloads.Verify.OK -> HistoryLog.add(ctx, "새 버전 받음 — 다운로드 알림을 눌러 설치")
                    ApkDownloads.Verify.CORRUPT -> {
                        HistoryLog.add(ctx, "받은 APK가 손상돼 지움 (sha256 불일치)")
                        Notifications.showUpdate(ctx, "받은 파일이 손상됐어요", "앱에서 다시 받아주세요")
                    }
                    ApkDownloads.Verify.FAILED -> HistoryLog.add(ctx, "새 버전 다운로드 실패")
                    ApkDownloads.Verify.NOT_OURS -> Unit
                }
            } catch (e: Exception) {
                HistoryLog.add(ctx, "다운로드 확인 오류: $e")
            } finally {
                pending.finish()
            }
        }
    }
}

/** 새 버전 알림의 [받기] → 다운로드 시작. 이 앱의 PendingIntent만 쓰므로 비공개(exported=false) */
class UpdateActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_START = "io.github.leepy0.strongalarm.UPDATE_DOWNLOAD"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_START) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remote = Updater.check(ctx)
                if (remote == null) {
                    Notifications.showUpdate(ctx, "새 버전을 확인하지 못했어요", "인터넷 연결을 확인하고 앱에서 다시 받아주세요")
                } else {
                    ApkDownloads.start(ctx, ApkDownloads.Kind.PHONE, remote.versionName, remote.phoneSha256)
                }
            } catch (e: Exception) {
                HistoryLog.add(ctx, "다운로드 시작 실패: $e")
                Notifications.showUpdate(ctx, "다운로드를 시작하지 못했어요", "앱에서 다시 받아주세요")
            } finally {
                pending.finish()
            }
        }
    }
}
