package io.github.leepy0.strongalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class OffStreaksTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val seven = LocalTime.of(7, 0)
    private val personal = 1L
    private val holidays = 9L
    private val rule = DayOffRule(holidayCalendarIds = setOf(holidays))

    // 2026-10-01(목) ~ 10-14(수). 10-03(토) 개천절, 10-09(금) 한글날
    private val thu = LocalDate.of(2026, 10, 1)

    private fun allDay(title: String, cal: Long, from: LocalDate, days: Long = 1) = CalendarEvent(
        title, cal, true,
        from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        from.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    private fun judgeAll(events: List<CalendarEvent>, days: Long = 14, confirmFor: Set<LocalDate> = emptySet()) =
        (0 until days).map { i ->
            val d = thu.plusDays(i)
            DayOffJudge.judge(d, seven, rule, events, zone, offNeedsConfirm = d in confirmFor)
        }

    @Test fun multiDayTripNeedsConfirmOnWeekdaysOnly() {
        // 10/1(목) ~ 10/5(월) 여행. 10/3(토) 개천절, 10/4(일) 주말 → 확인 대상은 목·금·월
        val trip = allDay("제주 여행", personal, thu, days = 5)
        val streaks = OffStreaks.find(judgeAll(listOf(trip, allDay("개천절", holidays, thu.plusDays(2)))))
        assertEquals(1, streaks.size)
        val s = streaks[0]
        assertEquals(thu, s.start)
        assertEquals(thu.plusDays(4), s.end)
        assertEquals(5, s.days)
        assertEquals(listOf(thu, thu.plusDays(1), thu.plusDays(4)), s.dates)
        assertEquals(listOf("제주 여행"), s.titles)
    }

    @Test fun singleOffDayIsNotAStreak() {
        // 금요일 연차 하나 + 주말 = 3일 쉬지만 휴무 일정은 하루뿐
        val fri = thu.plusDays(1)
        assertTrue(OffStreaks.find(judgeAll(listOf(allDay("연차", personal, fri)))).isEmpty())
    }

    @Test fun weekendOnlyTripIsNotAStreak() {
        // 토·일만 여행: 어차피 주말이라 확인 불필요
        val sat = thu.plusDays(9) // 10/10(토)
        assertTrue(OffStreaks.find(judgeAll(listOf(allDay("여행", personal, sat, days = 2)))).isEmpty())
    }

    @Test fun offDaysAcrossWeekendFormOneStreak() {
        // 10/8(목) 연차, 10/9(금) 한글날, 주말, 10/12(월) 연차 → 목~월 한 묶음, 확인 대상은 목·월
        val events = listOf(
            allDay("연차", personal, thu.plusDays(7)),
            allDay("한글날", holidays, thu.plusDays(8)),
            allDay("연차", personal, thu.plusDays(11)),
        )
        val streaks = OffStreaks.find(judgeAll(events))
        assertEquals(1, streaks.size)
        assertEquals(thu.plusDays(7), streaks[0].start)
        assertEquals(thu.plusDays(11), streaks[0].end)
        assertEquals(listOf(thu.plusDays(7), thu.plusDays(11)), streaks[0].dates)
    }

    @Test fun ringingDayBreaksStreak() {
        // 10/5(월) 연차, 10/6(화) 출근, 10/7(수) 연차 → 각각 하루라 묶음 없음
        val events = listOf(allDay("연차", personal, thu.plusDays(4)), allDay("연차", personal, thu.plusDays(6)))
        assertTrue(OffStreaks.find(judgeAll(events)).isEmpty())
    }

    @Test fun separateStreaksAreReportedSeparately() {
        val events = listOf(
            allDay("연차", personal, thu.plusDays(4), days = 2), // 10/5~10/6
            allDay("출장", personal, thu.plusDays(7), days = 2), // 10/8~10/9 (휴무 키워드 아님 → 출근)
            allDay("휴가", personal, thu.plusDays(12), days = 2), // 10/13~10/14
        )
        val streaks = OffStreaks.find(judgeAll(events))
        assertEquals(2, streaks.size)
        assertEquals(listOf("연차"), streaks[0].titles)
        assertEquals(listOf("휴가"), streaks[1].titles)
    }

    @Test fun pendingExcludesConfirmedDates() {
        val trip = allDay("여행", personal, thu.plusDays(4), days = 3) // 10/5~10/7
        val streaks = OffStreaks.find(judgeAll(listOf(trip)))
        val confirmed = setOf(thu.plusDays(4), thu.plusDays(5), thu.plusDays(6))
        assertTrue(OffStreaks.pendingDates(streaks, confirmed).isEmpty())
        // 여행이 하루 늘어나면 새 날짜만 확인 대상
        val longer = OffStreaks.find(judgeAll(listOf(allDay("여행", personal, thu.plusDays(4), days = 4))))
        assertEquals(setOf(thu.plusDays(7)), OffStreaks.pendingDates(longer, confirmed))
    }

    @Test fun unconfirmedWeekdayRingsButWeekendAndHolidayStillRest() {
        val trip = allDay("제주 여행", personal, thu, days = 5) // 10/1(목)~10/5(월)
        val events = listOf(trip, allDay("개천절", holidays, thu.plusDays(2)))
        val all = (0L..4L).map { thu.plusDays(it) }.toSet()
        val js = judgeAll(events, days = 5, confirmFor = all)
        assertTrue(js[0].ring)
        assertEquals(ReasonCode.OFF_UNCONFIRMED, js[0].code)
        assertEquals("제주 여행", js[0].detail)
        assertEquals(ReasonCode.HOLIDAY, js[2].code)
        assertFalse(js[2].ring)
        assertEquals(ReasonCode.WEEKEND, js[3].code)
        assertTrue(js[4].ring)
    }

    @Test fun eventOnlyFlagMarksDaysThatWouldOtherwiseRing() {
        val trip = allDay("여행", personal, thu.plusDays(1), days = 3) // 10/2(금)~10/4(일)
        val js = judgeAll(listOf(trip, allDay("개천절", holidays, thu.plusDays(2))))
        assertTrue(js[1].eventOnly) // 금
        assertFalse(js[2].eventOnly) // 토, 개천절
        assertFalse(js[3].eventOnly) // 일
        assertFalse(js[0].eventOnly) // 목: 일정 없음 (WEEKDAY)
    }

    @Test fun workAndOverrideStillWinOverConfirm() {
        val sat = thu.plusDays(2)
        val j = DayOffJudge.judge(thu, seven, rule, listOf(allDay("연차", personal, thu), allDay("특근", personal, thu)), zone, offNeedsConfirm = true)
        assertEquals(ReasonCode.WORK_EVENT, j.code)
        val o = DayOffJudge.judge(sat, seven, rule, listOf(allDay("연차", personal, sat)), zone, LocalTime.of(5, 0), offNeedsConfirm = true)
        assertEquals(ReasonCode.OVERRIDE, o.code)
        val m = DayOffJudge.judge(thu, seven, rule, listOf(allDay("연차", personal, thu)), zone, manualOff = true, offNeedsConfirm = true)
        assertEquals(ReasonCode.MANUAL_OFF, m.code)
    }
}
