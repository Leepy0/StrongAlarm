package io.github.leepy0.strongalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Phase
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.lights.LightController
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
                Stores.settings.update(ctx) { it.copy(overrides = it.overrides + (d.toString() to base.toString())) }
                HistoryLog.add(ctx, "그래도 울리기: $d $base")
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
            HistoryLog.add(ctx, "재등록 (${intent.action?.substringAfterLast('.')})")
        }
    }
}
