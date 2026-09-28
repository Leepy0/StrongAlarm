package io.github.leepy0.strongalarm.core

import java.time.LocalDate
import java.time.LocalTime

/** 캘린더 일정 1건 (Instances 기준). 종일 일정의 begin/end는 UTC 자정 기준 millis */
data class CalendarEvent(
    val title: String,
    val calendarId: Long,
    val allDay: Boolean,
    val begin: Long,
    val end: Long,
)

enum class ReasonCode {
    OVERRIDE,
    WORK_EVENT,
    OFF_EVENT,
    HOLIDAY,
    WEEKEND,
    WEEKDAY,
    CACHED,
    CALENDAR_UNAVAILABLE,
}

/** 캘린더를 읽을 수 없을 때(잠금 상태 부팅 등) 사용할 이전 판정 */
data class CachedDecision(val ring: Boolean, val reason: String)

data class Judgement(
    val date: LocalDate,
    val ring: Boolean,
    val time: LocalTime,
    val code: ReasonCode,
    val detail: String? = null,
) {
    fun describe(): String = when (code) {
        ReasonCode.OVERRIDE -> "일회성 변경 $time"
        ReasonCode.WORK_EVENT -> "근무 일정: $detail"
        ReasonCode.OFF_EVENT -> "휴무 일정: $detail"
        ReasonCode.HOLIDAY -> "공휴일: $detail"
        ReasonCode.WEEKEND -> "주말"
        ReasonCode.WEEKDAY -> "평일"
        ReasonCode.CACHED -> "이전 판정 사용 — $detail"
        ReasonCode.CALENDAR_UNAVAILABLE -> "캘린더 확인 불가 → 울림"
    }
}
