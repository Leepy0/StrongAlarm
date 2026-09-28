package io.github.leepy0.strongalarm.core

import kotlinx.serialization.Serializable

/**
 * 휴무 판정 규칙. 설정 화면에서 편집한다.
 *
 * 우선순위: 일회성 변경 > 근무 키워드 > 휴무 키워드 > 공휴일 캘린더 > 주말 > 평일
 */
@Serializable
data class DayOffRule(
    /** 일정 제목에 포함되면 스킵 */
    val offKeywords: List<String> = listOf("여행", "연차", "휴무", "디데이", "휴가"),
    /** 일정 제목에 포함되면 주말·공휴일이어도 울림 */
    val workKeywords: List<String> = listOf("특근"),
    /** 휴무 키워드와 함께 있으면 휴무로 보지 않음 (예: "연차 신청 마감") */
    val excludeKeywords: List<String> = listOf("신청", "마감"),
    /** 키워드 판정 대상 캘린더. 비어 있으면 공휴일 캘린더를 제외한 전체 */
    val calendarIds: Set<Long> = emptySet(),
    /** 공휴일 캘린더 (예: Google "대한민국의 휴일") — 이 캘린더의 일정이 있으면 스킵 */
    val holidayCalendarIds: Set<Long> = emptySet(),
    /** 공휴일 캘린더에 섞여 있는 비공휴일 기념일 제외 */
    val holidayExcludeKeywords: List<String> = listOf("어버이날", "스승의 날", "식목일", "제헌절"),
    /** true면 종일 일정만 키워드 판정에 사용 */
    val allDayOnly: Boolean = false,
)
