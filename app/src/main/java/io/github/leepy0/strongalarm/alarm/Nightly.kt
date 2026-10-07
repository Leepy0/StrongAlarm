package io.github.leepy0.strongalarm.alarm

import android.content.Context
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.lights.SmartThingsAuth
import java.time.LocalDateTime

/** 매일 23시: 내일 판정 안내 + 재등록 + SmartThings 토큰 유지 */
object Nightly {
    suspend fun run(ctx: Context) {
        val now = LocalDateTime.now()
        val target = if (now.hour >= 12) now.toLocalDate().plusDays(1) else now.toLocalDate()

        Stores.state.update(ctx) { it.copy(lastNightlyAt = System.currentTimeMillis()) }
        AlarmScheduler.rescheduleAll(ctx)
        val j = AlarmScheduler.judge(ctx, target)
        Notifications.showNightly(ctx, j)
        HistoryLog.add(ctx, "안내: $target ${if (j.ring) "울림 ${j.time}" else "스킵"} — ${j.describe()}")

        // access token 만료 시 갱신 (refresh token 29일 미사용 만료 방지)
        if (SmartThingsAuth.isLoggedIn(ctx) && SmartThingsAuth.accessToken(ctx) == null) {
            HistoryLog.add(ctx, "SmartThings 토큰 갱신 실패 — 재로그인 필요")
        }
    }
}
