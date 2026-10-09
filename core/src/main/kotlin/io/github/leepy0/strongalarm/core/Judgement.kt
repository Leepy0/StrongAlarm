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
    /** 앱에서 그날을 직접 쉬는 날로 지정 */
    MANUAL_OFF,
    WORK_EVENT,
    OFF_EVENT,
    /** 휴무 일정이 2일 이상 이어지는데 아직 안 울려도 된다고 확인하지 않음 → 평일처럼 울림 */
    OFF_UNCONFIRMED,
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
    /** OFF_EVENT일 때: 휴무 일정이 없었다면 울렸을 날 (주말·공휴일이 아님). 연속 휴무 확인 대상 */
    val eventOnly: Boolean = false,
) {
    fun describe(): String = when (code) {
        ReasonCode.OVERRIDE -> "일회성 변경 $time"
        ReasonCode.MANUAL_OFF -> "직접 쉬는 날로 지정"
        ReasonCode.WORK_EVENT -> "근무 일정: $detail"
        ReasonCode.OFF_EVENT -> "휴무 일정: $detail"
        ReasonCode.OFF_UNCONFIRMED -> "연속 휴무 일정 확인 전: $detail"
        ReasonCode.HOLIDAY -> "공휴일: $detail"
        ReasonCode.WEEKEND -> "주말"
        ReasonCode.WEEKDAY -> "평일"
        ReasonCode.CACHED -> "이전 판정 사용 — $detail"
        ReasonCode.CALENDAR_UNAVAILABLE -> "캘린더 확인 불가 → 울림"
    }
}
