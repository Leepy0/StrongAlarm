package io.github.leepy0.strongalarm.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

object DayOffJudge {

    /**
     * @param events 알람 날짜 주변 일정. null이면 캘린더를 읽을 수 없는 상태
     * @param overrideTime 일회성 변경 시각. 있으면 무조건 그 시각에 울림
     * @param cached 캘린더를 읽을 수 없을 때 사용할 이전 판정
     */
    fun judge(
        date: LocalDate,
        baseTime: LocalTime,
        rule: DayOffRule,
        events: List<CalendarEvent>?,
        zone: ZoneId,
        overrideTime: LocalTime? = null,
        cached: CachedDecision? = null,
    ): Judgement {
        // 0순위: 일회성 변경
        if (overrideTime != null) {
            return Judgement(date, true, overrideTime, ReasonCode.OVERRIDE)
        }

        // 캘린더 확인 불가 → 이전 판정, 없으면 울림 (fail-safe)
        if (events == null) {
            return if (cached != null) {
                Judgement(date, cached.ring, baseTime, ReasonCode.CACHED, cached.reason)
            } else {
                Judgement(date, true, baseTime, ReasonCode.CALENDAR_UNAVAILABLE)
            }
        }

        val todays = events.filter { overlaps(it, date, zone) }
        val keywordTargets = todays.filter {
            it.calendarId !in rule.holidayCalendarIds &&
                (rule.calendarIds.isEmpty() || it.calendarId in rule.calendarIds) &&
                (!rule.allDayOnly || it.allDay)
        }

        // 1순위: 근무 키워드
        keywordTargets.firstOrNull { KeywordMatcher.firstHit(rule.workKeywords, it.title) != null }
            ?.let { return Judgement(date, true, baseTime, ReasonCode.WORK_EVENT, it.title) }

        // 2순위: 휴무 키워드 (제외 키워드가 함께 있으면 무시)
        keywordTargets.firstOrNull {
            KeywordMatcher.firstHit(rule.excludeKeywords, it.title) == null &&
                KeywordMatcher.firstHit(rule.offKeywords, it.title) != null
        }?.let { return Judgement(date, false, baseTime, ReasonCode.OFF_EVENT, it.title) }

        // 3순위: 공휴일 캘린더
        todays.firstOrNull {
            it.calendarId in rule.holidayCalendarIds &&
                KeywordMatcher.firstHit(rule.holidayExcludeKeywords, it.title) == null
        }?.let { return Judgement(date, false, baseTime, ReasonCode.HOLIDAY, it.title) }

        // 4순위: 주말
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return Judgement(date, false, baseTime, ReasonCode.WEEKEND)
        }
        return Judgement(date, true, baseTime, ReasonCode.WEEKDAY)
    }

    /** 일정이 해당 날짜와 겹치는지 */
    fun overlaps(e: CalendarEvent, date: LocalDate, zone: ZoneId): Boolean {
        return if (e.allDay) {
            // 종일 일정: UTC 자정 기준으로 저장됨. end는 다음날 0시라 미포함
            val start = Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate()
            var end = Instant.ofEpochMilli(e.end).atZone(ZoneOffset.UTC).toLocalDate()
            if (!end.isAfter(start)) end = start.plusDays(1)
            !date.isBefore(start) && date.isBefore(end)
        } else {
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = if (e.end > e.begin) e.end else e.begin + 1
            e.begin < dayEnd && end > dayStart
        }
    }
}
