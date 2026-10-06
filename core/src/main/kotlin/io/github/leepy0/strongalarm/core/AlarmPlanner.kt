package io.github.leepy0.strongalarm.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

object AlarmPlanner {

    /** now 이후 첫 번째 울림. 오늘 시각이 이미 지났으면 다음 날부터 */
    fun nextRing(
        now: ZonedDateTime,
        maxDays: Int = 62,
        judgeFor: (LocalDate) -> Judgement,
    ): Judgement? {
        val today = now.toLocalDate()
        for (i in 0..maxDays) {
            val date = today.plusDays(i.toLong())
            val j = judgeFor(date)
            if (!j.ring) continue
            if (date.atTime(j.time).atZone(now.zone).isAfter(now)) return j
        }
        return null
    }

    fun ringAtMillis(j: Judgement, zone: ZoneId): Long =
        j.date.atTime(j.time).atZone(zone).toInstant().toEpochMilli()

    /**
     * 디밍 시작 시각. 이미 지났지만 울림 전이면 즉시(now + 2초)
     * @return null이면 디밍 없음
     */
    fun preAlarmAt(ringAt: Long, leadMinutes: Int, now: Long): Long? {
        if (leadMinutes <= 0 || ringAt <= now) return null
        val pre = ringAt - leadMinutes * 60_000L
        return if (pre > now) pre else (now + 2_000L).takeIf { it < ringAt }
    }
}

object DimRamp {
    /** start~end 구간에서 1% → target% 선형 증가 */
    fun levelAt(now: Long, start: Long, end: Long, target: Int): Int {
        val t = target.coerceIn(1, 100)
        if (end <= start || now >= end) return t
        if (now <= start) return 1
        val frac = (now - start).toDouble() / (end - start)
        return (1 + (t - 1) * frac).roundToInt().coerceIn(1, t)
    }
}

/** 남은 시간 문구: "6시간 15분", "1일 3시간", "12분". 분은 올림(0분 표시 방지) */
object Countdown {
    fun text(diffMs: Long): String {
        if (diffMs <= 0) return "곧"
        val totalMin = (diffMs + 59_999) / 60_000
        val days = totalMin / (24 * 60)
        val hours = totalMin % (24 * 60) / 60
        val mins = totalMin % 60
        return when {
            days > 0 -> if (hours > 0) "${days}일 ${hours}시간" else "${days}일"
            hours > 0 -> if (mins > 0) "${hours}시간 ${mins}분" else "${hours}시간"
            else -> "${mins}분"
        }
    }
}
