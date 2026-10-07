package io.github.leepy0.strongalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Phase
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.data.dayLocked
import io.github.leepy0.strongalarm.lights.LightController
import io.github.leepy0.strongalarm.update.ApkDownloads
import io.github.leepy0.strongalarm.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** goAsync + 코루틴. 브로드캐스트 제한 시간 안에 끝나도록 타임아웃 */
private fun BroadcastReceiver.goAsyncWork(timeoutMs: Long = 25_000, block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            withTimeoutOrNull(timeoutMs) { block() }
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val date = intent.getStringExtra(AlarmScheduler.EXTRA_DATE)
        when (intent.action) {
            // 정확한 알람 트리거 → 백그라운드 FGS 시작 허용
            AlarmScheduler.ACTION_PRE,
            AlarmScheduler.ACTION_RING,
            AlarmScheduler.ACTION_TEST,
            -> AlarmService.start(ctx, intent.action!!, date)

            AlarmScheduler.ACTION_NIGHTLY -> goAsyncWork { Nightly.run(ctx) }

            AlarmScheduler.ACTION_LIGHTS_OFF -> goAsyncWork { LightController.autoOff(ctx) }

            AlarmScheduler.ACTION_FORCE_RING -> goAsyncWork {
                val d = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@goAsyncWork
                val base = Stores.settings.get(ctx).baseTime
                Stores.settings.update(ctx) { it.withTime(d, base) }
                HistoryLog.add(ctx, "그래도 울리기: $d $base")
                AlarmScheduler.rescheduleAll(ctx)
                Notifications.showNightly(ctx, AlarmScheduler.judge(ctx, d))
            }

            // 23시 안내 [울림 확인]: 그날 알람은 쉬는 날 버튼 잠금
            AlarmScheduler.ACTION_CONFIRM -> goAsyncWork {
                val d = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@goAsyncWork
                Stores.settings.update(ctx) { it.withConfirm(d) }
                HistoryLog.add(ctx, "울림 확인: $d (쉬는 날 버튼 잠금)")
                Notifications.showNightly(ctx, AlarmScheduler.judge(ctx, d))
            }

            // 23시 안내 [쉬는 날로]. 알람이 진행 중인 날짜면 무시
            AlarmScheduler.ACTION_SKIP -> goAsyncWork {
                val d = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@goAsyncWork
                if (Stores.state.get(ctx).dayLocked(d)) {
                    HistoryLog.add(ctx, "쉬는 날 지정 무시: $d 알람 진행 중")
                    return@goAsyncWork
                }
                Stores.settings.update(ctx) { it.withSkip(d) }
                HistoryLog.add(ctx, "쉬는 날로 지정: $d")
                AlarmScheduler.rescheduleAll(ctx)
                Notifications.showNightly(ctx, AlarmScheduler.judge(ctx, d))
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        goAsyncWork {
            AlarmScheduler.rescheduleAll(ctx)
            // 자동 소등 대기 중 재부팅된 경우 1분 뒤 소등
            if (Stores.state.get(ctx).session?.phase == Phase.DONE) {
                AlarmScheduler.scheduleLightsOff(ctx, System.currentTimeMillis() + 60_000)
            }
            if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                ctx.getSystemService(android.app.NotificationManager::class.java).cancel(Notifications.ID_UPDATE)
                ApkDownloads.cleanup(ctx)
                HistoryLog.add(ctx, "업데이트 완료 → ${Updater.installedVersionName(ctx)}")
            }
            HistoryLog.add(ctx, "재등록 (${intent.action?.substringAfterLast('.')})")
        }
    }
}
