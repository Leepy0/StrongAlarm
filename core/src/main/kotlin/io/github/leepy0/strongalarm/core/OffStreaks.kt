package io.github.leepy0.strongalarm.core

import java.time.LocalDate

/**
 * 연속 휴무 일정 묶음: 쉬는 날이 이어지는 구간(start~end) 중 휴무 일정 때문에만 쉬는 날이 [MIN_DAYS] 이상인 것.
 * 사용자가 "안 울려도 돼요"라고 확인하기 전에는 그 날들을 평일처럼 울린다 (캘린더 일정 하나가 며칠을 조용히 삼키지 않게).
 *
 * @param dates 휴무 일정 때문에만 쉬는 날짜 (주말·공휴일은 제외) — 확인 대상
 * @param titles 그 날들의 일정 제목 (중복 제거)
 */
data class OffStreak(
    val start: LocalDate,
    val end: LocalDate,
    val dates: List<LocalDate>,
    val titles: List<String>,
) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt() + 1

    /** 아직 확인하지 않은 날짜 */
    fun pending(confirmed: Set<LocalDate>): List<LocalDate> = dates.filterNot { it in confirmed }
}

object OffStreaks {
    /** 휴무 일정 때문에만 쉬는 날이 이 수 이상 이어지면 확인 필요 */
    const val MIN_DAYS = 2

    /**
     * 날짜순으로 이어진 판정(휴무 확인 미적용)에서 연속 휴무 일정 묶음을 찾는다.
     * 쉬는 날이 끊기지 않고 이어지는 구간마다, 그 안에 OFF_EVENT이면서 eventOnly인 날이 [MIN_DAYS] 이상이면 묶음.
     * 주말·공휴일이 사이에 끼어 있어도 한 묶음으로 본다 (금 연차 + 주말 + 월 연차 = 4일 연속).
     */
    fun find(judgements: List<Judgement>): List<OffStreak> {
        val out = mutableListOf<OffStreak>()
        var run = mutableListOf<Judgement>()
        fun flush() {
            val targets = run.filter { it.code == ReasonCode.OFF_EVENT && it.eventOnly }
            if (targets.size >= MIN_DAYS) {
                out += OffStreak(
                    start = run.first().date,
                    end = run.last().date,
                    dates = targets.map { it.date },
                    titles = targets.mapNotNull { it.detail }.distinct(),
                )
            }
            run = mutableListOf()
        }
        for (j in judgements.sortedBy { it.date }) {
            val contiguous = run.lastOrNull()?.let { it.date.plusDays(1) == j.date } ?: true
            if (j.ring || !contiguous) flush()
            if (!j.ring) run += j
        }
        flush()
        return out
    }

    /** 확인이 필요한 날짜 전체 (확인된 날짜 제외) */
    fun pendingDates(streaks: List<OffStreak>, confirmed: Set<LocalDate>): Set<LocalDate> =
        streaks.flatMap { it.pending(confirmed) }.toSet()
}
