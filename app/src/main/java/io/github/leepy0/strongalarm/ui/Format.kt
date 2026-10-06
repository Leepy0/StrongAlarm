package io.github.leepy0.strongalarm.ui

import io.github.leepy0.strongalarm.core.Judgement
import io.github.leepy0.strongalarm.core.ReasonCode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** 10/2(금) */
fun LocalDate.pretty(): String = "$monthValue/$dayOfMonth(${weekdayShort()})"

/** 10월 2일 금요일 */
fun LocalDate.longKo(): String =
    "${monthValue}월 ${dayOfMonth}일 ${dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}"

/** 금 */
fun LocalDate.weekdayShort(): String = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

/** 오늘·내일·모레, 그 외에는 날짜 */
fun LocalDate.relativeKo(today: LocalDate): String = when (this) {
    today -> "오늘"
    today.plusDays(1) -> "내일"
    today.plusDays(2) -> "모레"
    else -> longKo()
}

/** 07:00 */
fun LocalTime.hhmm(): String = "%02d:%02d".format(hour, minute)

/** 판정 사유를 사용자 문장으로 */
fun Judgement.sentence(): String = when (code) {
    ReasonCode.OVERRIDE -> "이 날만 시각을 바꿔뒀어요"
    ReasonCode.WORK_EVENT -> "근무 일정이 있어요: $detail"
    ReasonCode.OFF_EVENT -> "휴무 일정이 있어요: $detail"
    ReasonCode.HOLIDAY -> "공휴일이에요: $detail"
    ReasonCode.WEEKEND -> "주말이에요"
    ReasonCode.WEEKDAY -> "평일이에요"
    ReasonCode.CACHED -> "캘린더를 읽지 못해 이전 판정을 따라요 ($detail)"
    ReasonCode.CALENDAR_UNAVAILABLE -> "캘린더를 읽지 못해 안전하게 울려요"
}
