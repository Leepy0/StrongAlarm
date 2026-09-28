package io.github.leepy0.strongalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.leepy0.strongalarm.core.AlarmPlanner
import io.github.leepy0.strongalarm.core.CachedDecision
import io.github.leepy0.strongalarm.core.DayOffJudge
import io.github.leepy0.strongalarm.core.Judgement
import io.github.leepy0.strongalarm.data.CachedEntry
import io.github.leepy0.strongalarm.data.CalendarReader
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.NextPlan
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.ui.MainActivity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmScheduler {
    private const val PKG = "io.github.leepy0.strongalarm"
    const val ACTION_PRE = "$PKG.PRE"
    const val ACTION_RING = "$PKG.RING"
    const val ACTION_TEST = "$PKG.TEST"
    const val ACTION_NIGHTLY = "$PKG.NIGHTLY"
    const val ACTION_LIGHTS_OFF = "$PKG.LIGHTS_OFF"
    const val ACTION_FORCE_RING = "$PKG.FORCE_RING"
    const val EXTRA_DATE = "date"

    private const val REQ_RING = 1
    private const val REQ_PRE = 2
    private const val REQ_NIGHTLY = 3
    private const val REQ_LIGHTS_OFF = 4
    private const val REQ_TEST = 5

    private const val PLAN_DAYS = 62

    /** 판정에 필요한 입력. 캘린더는 범위 전체를 한 번만 조회 */
    private class JudgeInput(ctx: Context, from: LocalDate, days: Int) {
        val settings = Stores.settings.get(ctx)
        val zone: ZoneId = ZoneId.systemDefault()
        val events = CalendarReader.readEvents(ctx, from, from.plusDays(days.toLong()), zone)
        val cache = Stores.state.get(ctx).cache

        fun judge(date: LocalDate, useOverride: Boolean = true): Judgement = DayOffJudge.judge(
            date = date,
            baseTime = settings.baseTime,
            rule = settings.rule,
            events = events,
            zone = zone,
            overrideTime = if (useOverride) settings.overrideFor(date) else null,
            cached = cache[date.toString()]?.let { CachedDecision(it.ring, it.reason) },
        )
    }

    fun judgeFunction(ctx: Context, from: LocalDate, days: Int): (LocalDate) -> Judgement {
        val input = JudgeInput(ctx, from, days)
        return { input.judge(it) }
    }

    fun judge(ctx: Context, date: LocalDate): Judgement = JudgeInput(ctx, date, 1).judge(date)

    /** 다음 알람·디밍·23시 안내를 모두 다시 등록. 설정·캘린더 변경, 부팅, 알람 종료 후 호출 */
    @Synchronized
    fun rescheduleAll(ctx: Context) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val settings = Stores.settings.get(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)

        pruneOverrides(ctx, today)
        val input = JudgeInput(ctx, today, PLAN_DAYS + 1)
        updateCache(ctx, today, input)

        am.cancel(pending(ctx, ACTION_RING, REQ_RING, null))
        am.cancel(pending(ctx, ACTION_PRE, REQ_PRE, null))

        val next = AlarmPlanner.nextRing(now, PLAN_DAYS) { input.judge(it) }
        if (next != null) {
            val ringAt = AlarmPlanner.ringAtMillis(next, zone)
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(ringAt, show), pending(ctx, ACTION_RING, REQ_RING, next.date))
                if (settings.lights.dimmerIds.isNotEmpty()) {
                    AlarmPlanner.preAlarmAt(ringAt, settings.lights.dimLeadMinutes, System.currentTimeMillis())?.let {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it, pending(ctx, ACTION_PRE, REQ_PRE, next.date))
                    }
                }
            } catch (e: SecurityException) {
                HistoryLog.add(ctx, "알람 등록 실패 (정확한 알람 권한): ${e.message}")
            }
            Stores.state.update(ctx) {
                it.copy(next = NextPlan(next.date.toString(), next.time.toString(), next.describe(), ringAt))
            }
        } else {
            Stores.state.update(ctx) { it.copy(next = null) }
        }

        scheduleNightly(ctx)
        CalendarChangeJob.schedule(ctx)
    }

    /** 캘린더를 읽을 수 있을 때 오늘~모레 판정을 캐시 (잠금 상태 재부팅 대비). 일회성 변경은 제외하고 저장 */
    private fun updateCache(ctx: Context, today: LocalDate, input: JudgeInput) {
        if (input.events == null) return
        val fresh = (0L..2L).associate { i ->
            val j = input.judge(today.plusDays(i), useOverride = false)
            j.date.toString() to CachedEntry(j.ring, j.describe())
        }
        Stores.state.update(ctx) { st ->
            val kept = st.cache.filterKeys {
                runCatching { !LocalDate.parse(it).isBefore(today.minusDays(1)) }.getOrDefault(false)
            }
            st.copy(cache = kept + fresh)
        }
    }

    private fun pruneOverrides(ctx: Context, today: LocalDate) {
        Stores.settings.update(ctx) { s ->
            s.copy(overrides = s.overrides.filterKeys { runCatching { !LocalDate.parse(it).isBefore(today) }.getOrDefault(false) })
        }
    }

    fun scheduleNightly(ctx: Context) {
        val s = Stores.settings.get(ctx)
        val now = ZonedDateTime.now()
        var at = now.toLocalDate().atTime(s.nightlyHour, s.nightlyMinute).atZone(now.zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        exact(ctx, at.toInstant().toEpochMilli(), pending(ctx, ACTION_NIGHTLY, REQ_NIGHTLY, null))
    }

    fun scheduleLightsOff(ctx: Context, atMillis: Long) =
        exact(ctx, atMillis, pending(ctx, ACTION_LIGHTS_OFF, REQ_LIGHTS_OFF, null))

    fun scheduleTest(ctx: Context, delayMs: Long) =
        exact(ctx, System.currentTimeMillis() + delayMs, pending(ctx, ACTION_TEST, REQ_TEST, LocalDate.now()))

    private fun exact(ctx: Context, at: Long, pi: PendingIntent) {
        try {
            ctx.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            HistoryLog.add(ctx, "정확한 알람 등록 실패: ${e.message}")
        }
    }

    private fun pending(ctx: Context, action: String, req: Int, date: LocalDate?): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, AlarmReceiver::class.java).setAction(action).apply {
                date?.let { putExtra(EXTRA_DATE, it.toString()) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
