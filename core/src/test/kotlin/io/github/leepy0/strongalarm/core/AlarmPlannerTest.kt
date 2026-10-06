package io.github.leepy0.strongalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AlarmPlannerTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val seven = LocalTime.of(7, 0)
    private val rule = DayOffRule()

    private fun judgeFor(overrides: Map<LocalDate, LocalTime> = emptyMap()): (LocalDate) -> Judgement = { d ->
        DayOffJudge.judge(d, seven, rule, emptyList(), zone, overrides[d])
    }

    @Test fun beforeTimeTodayPicksToday() {
        val now = LocalDate.of(2026, 10, 1).atTime(6, 0).atZone(zone) // 목
        assertEquals(LocalDate.of(2026, 10, 1), AlarmPlanner.nextRing(now, judgeFor = judgeFor())?.date)
    }

    @Test fun afterTimeTodayPicksNextWorkday() {
        val now = LocalDate.of(2026, 10, 2).atTime(7, 0).atZone(zone) // 금 07:00 정각 = 지남
        assertEquals(LocalDate.of(2026, 10, 5), AlarmPlanner.nextRing(now, judgeFor = judgeFor())?.date)
    }

    @Test fun overrideLaterToday() {
        val today = LocalDate.of(2026, 10, 1)
        val now = today.atTime(7, 30).atZone(zone)
        val j = AlarmPlanner.nextRing(now, judgeFor = judgeFor(mapOf(today to LocalTime.of(9, 0))))
        assertEquals(today, j?.date)
        assertEquals(LocalTime.of(9, 0), j?.time)
    }

    @Test fun noRingWithinRange() {
        val now = LocalDate.of(2026, 10, 1).atTime(8, 0).atZone(zone)
        val never: (LocalDate) -> Judgement = { d -> Judgement(d, false, seven, ReasonCode.WEEKEND) }
        assertNull(AlarmPlanner.nextRing(now, 10, never))
    }

    @Test fun preAlarmTiming() {
        val ringAt = 1_000_000_000L
        assertEquals(ringAt - 15 * 60_000L, AlarmPlanner.preAlarmAt(ringAt, 15, ringAt - 60 * 60_000L))
        // 디밍 시작 시각이 지났으면 즉시
        val now = ringAt - 5 * 60_000L
        assertEquals(now + 2_000L, AlarmPlanner.preAlarmAt(ringAt, 15, now))
        // 울림 시각이 지났으면 없음
        assertNull(AlarmPlanner.preAlarmAt(ringAt, 15, ringAt + 1))
        assertNull(AlarmPlanner.preAlarmAt(ringAt, 0, now))
    }

    @Test fun dimRampIsLinear() {
        assertEquals(1, DimRamp.levelAt(0, 0, 900, 100))
        assertEquals(51, DimRamp.levelAt(450, 0, 900, 100))
        assertEquals(100, DimRamp.levelAt(900, 0, 900, 100))
        assertEquals(100, DimRamp.levelAt(2000, 0, 900, 100))
        assertEquals(1, DimRamp.levelAt(-5, 0, 900, 100))
    }

    @Test fun countdownText() {
        val min = 60_000L
        assertEquals("곧", Countdown.text(0))
        assertEquals("1분", Countdown.text(1_000))
        assertEquals("15분", Countdown.text(15 * min))
        assertEquals("6시간 15분", Countdown.text((6 * 60 + 14) * min + 30_000))
        assertEquals("2시간", Countdown.text(120 * min))
        assertEquals("1일 3시간", Countdown.text((27 * 60 + 20) * min))
        assertEquals("2일", Countdown.text(48 * 60 * min))
    }

    @Test fun wearIntCodec() {
        assertEquals(30, WearProtocol.decodeInt(WearProtocol.encodeInt(30)))
        assertNull(WearProtocol.decodeInt("x".toByteArray()))
        assertNull(WearProtocol.decodeInt(null))
    }
}
