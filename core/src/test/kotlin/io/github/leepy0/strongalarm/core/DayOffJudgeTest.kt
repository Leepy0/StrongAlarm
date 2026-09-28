package io.github.leepy0.strongalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class DayOffJudgeTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val seven = LocalTime.of(7, 0)
    private val personal = 1L
    private val shared = 2L
    private val holidays = 9L
    private val rule = DayOffRule(holidayCalendarIds = setOf(holidays))

    // 2026-10-01(목), 10-03(토) 개천절, 10-05(월)
    private val thu = LocalDate.of(2026, 10, 1)
    private val sat = LocalDate.of(2026, 10, 3)
    private val mon = LocalDate.of(2026, 10, 5)

    private fun allDay(title: String, cal: Long, from: LocalDate, days: Long = 1) = CalendarEvent(
        title, cal, true,
        from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        from.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    private fun timed(title: String, cal: Long, date: LocalDate, h1: Int, h2: Int) = CalendarEvent(
        title, cal, false,
        date.atTime(h1, 0).atZone(zone).toInstant().toEpochMilli(),
        date.atTime(h2, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    private fun judge(date: LocalDate, events: List<CalendarEvent>?, r: DayOffRule = rule, override: LocalTime? = null, cached: CachedDecision? = null) =
        DayOffJudge.judge(date, seven, r, events, zone, override, cached)

    @Test fun weekdayRings() {
        val j = judge(thu, emptyList())
        assertTrue(j.ring)
        assertEquals(ReasonCode.WEEKDAY, j.code)
        assertEquals(seven, j.time)
    }

    @Test fun weekendSkips() {
        val j = judge(sat, emptyList())
        assertFalse(j.ring)
        assertEquals(ReasonCode.WEEKEND, j.code)
    }

    @Test fun holidayCalendarSkips() {
        val j = judge(mon, listOf(allDay("대체공휴일", holidays, mon)))
        assertFalse(j.ring)
        assertEquals(ReasonCode.HOLIDAY, j.code)
    }

    @Test fun holidayObservanceIsIgnored() {
        val j = judge(thu, listOf(allDay("어버이날", holidays, thu)))
        assertTrue(j.ring)
    }

    @Test fun holidayCalendarNotUsedForKeywords() {
        // 공휴일 캘린더의 "휴무" 문자열은 키워드 판정에 쓰지 않음 (공휴일 규칙으로만 처리)
        val j = judge(thu, listOf(allDay("임시 휴무", holidays, thu)), r = rule.copy(holidayExcludeKeywords = listOf("휴무")))
        assertTrue(j.ring)
    }

    @Test fun offKeywordSkips() {
        val j = judge(thu, listOf(allDay("평영 연차", personal, thu)))
        assertFalse(j.ring)
        assertEquals(ReasonCode.OFF_EVENT, j.code)
        assertEquals("평영 연차", j.detail)
    }

    @Test fun keywordIgnoresSpacesAndCase() {
        val r = rule.copy(offKeywords = listOf("D-Day"))
        assertFalse(judge(thu, listOf(allDay("결혼기념일 d - day", personal, thu)), r).ring)
    }

    @Test fun excludeKeywordBlocksOff() {
        val j = judge(thu, listOf(allDay("연차 신청 마감", personal, thu)))
        assertTrue(j.ring)
    }

    @Test fun workKeywordRingsOnWeekend() {
        val j = judge(sat, listOf(timed("특근", personal, sat, 8, 17)))
        assertTrue(j.ring)
        assertEquals(ReasonCode.WORK_EVENT, j.code)
    }

    @Test fun workKeywordBeatsHolidayAndOff() {
        val events = listOf(allDay("개천절", holidays, sat), allDay("휴무", personal, sat), allDay("특근", personal, sat))
        assertTrue(judge(sat, events).ring)
    }

    @Test fun multiDayTripSkipsEveryDay() {
        val trip = allDay("제주 여행", personal, thu, days = 3) // 10/1 ~ 10/3
        assertFalse(judge(thu, listOf(trip)).ring)
        assertFalse(judge(thu.plusDays(1), listOf(trip)).ring)
        // 종료일(end, 10/4)은 미포함 → 10/4 일요일은 주말 규칙
        assertEquals(ReasonCode.WEEKEND, judge(thu.plusDays(3), listOf(trip)).code)
        // 다음 주 월요일은 영향 없음
        assertTrue(judge(mon, listOf(trip)).ring)
    }

    @Test fun timedEventOnOtherDayIgnored() {
        val j = judge(thu, listOf(timed("연차", personal, thu.minusDays(1), 9, 18)))
        assertTrue(j.ring)
    }

    @Test fun calendarFilterExcludesSharedCalendar() {
        val r = rule.copy(calendarIds = setOf(personal))
        assertTrue(judge(thu, listOf(allDay("아름 연차", shared, thu)), r).ring)
        assertFalse(judge(thu, listOf(allDay("연차", personal, thu)), r).ring)
    }

    @Test fun allDayOnlyIgnoresTimedEvents() {
        val r = rule.copy(allDayOnly = true)
        assertTrue(judge(thu, listOf(timed("휴가 계획 회의", personal, thu, 10, 11)), r).ring)
    }

    @Test fun blankKeywordNeverMatches() {
        val r = rule.copy(offKeywords = listOf("", "  "))
        assertTrue(judge(thu, listOf(allDay("아무 일정", personal, thu)), r).ring)
    }

    @Test fun overrideForcesRingAtGivenTime() {
        val t = LocalTime.of(5, 30)
        val j = judge(sat, listOf(allDay("연차", personal, sat)), override = t)
        assertTrue(j.ring)
        assertEquals(t, j.time)
        assertEquals(ReasonCode.OVERRIDE, j.code)
    }

    @Test fun calendarUnavailableUsesCache() {
        val j = judge(thu, null, cached = CachedDecision(false, "휴무 일정: 연차"))
        assertFalse(j.ring)
        assertEquals(ReasonCode.CACHED, j.code)
    }

    @Test fun calendarUnavailableWithoutCacheRings() {
        val j = judge(sat, null)
        assertTrue(j.ring)
        assertEquals(ReasonCode.CALENDAR_UNAVAILABLE, j.code)
    }

    @Test fun keywordMatcherReturnsNullForNoHit() {
        assertNull(KeywordMatcher.firstHit(listOf("연차"), "회의"))
    }
}
