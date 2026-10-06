package io.github.leepy0.strongalarm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.core.Countdown
import io.github.leepy0.strongalarm.ui.agoKo
import io.github.leepy0.strongalarm.ui.components.ScreenPadding
import io.github.leepy0.strongalarm.ui.components.SectionLabel
import io.github.leepy0.strongalarm.ui.components.StatusLine
import io.github.leepy0.strongalarm.ui.hhmm
import io.github.leepy0.strongalarm.ui.longKo
import io.github.leepy0.strongalarm.ui.pretty
import io.github.leepy0.strongalarm.ui.theme.Palette
import io.github.leepy0.strongalarm.ui.weekdayShort
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 2주 격자의 하루 */
data class DayCell(
    val date: LocalDate,
    val ring: Boolean,
    val time: LocalTime,
    /** 일회성 변경으로 시각을 바꾼 날 */
    val overridden: Boolean,
    /** 원 아래 짧은 표시: 울리는 날은 시각, 쉬는 날은 이유 */
    val label: String,
    /** 시트에 보여줄 이유 문장 */
    val reason: String,
)

data class NextAlarm(val date: LocalDate, val time: LocalTime, val reason: String, val ringAt: Long)

data class Readiness(
    val missingPermissions: Int,
    /** null = 확인 중 */
    val watchNodes: Int?,
    /** null = 조명 미사용 */
    val lightsLinked: Int?,
    val holidayCalendarSet: Boolean,
    val calendarReadable: Boolean,
    /** 마지막으로 일정을 확인한 시각 (null = 아직 없음) */
    val lastCheck: Long? = null,
    /** 마지막 확인 때 캘린더를 읽었는지 */
    val lastCheckCalendarOk: Boolean = true,
    /** 지난 알람 결과 문장과 시각 */
    val lastResult: String? = null,
    val lastResultAt: Long? = null,
    /** SmartThings 재로그인 필요 */
    val lightsAuthError: Boolean = false,
)

data class HomeUiState(
    val today: LocalDate,
    val next: NextAlarm?,
    val baseTime: LocalTime,
    /** 비어 있으면 판정 중(로딩) */
    val days: List<DayCell>,
    val ringing: Boolean,
    val dimming: Boolean,
    val readiness: Readiness,
    /** 'n분 전' 계산 기준 (테스트에서 고정) */
    val nowMillis: Long = System.currentTimeMillis(),
)

/** 오늘·내일·모레 또는 10/9(금) */
private fun LocalDate.shortRelative(today: LocalDate): String = when (this) {
    today -> "오늘"
    today.plusDays(1) -> "내일"
    today.plusDays(2) -> "모레"
    else -> pretty()
}

/**
 * 알람 탭. 항상 보여야 할 상태 = 다음 알람, 주요 행동 = 그날만 바꾸기(엄지 영역 쪽에 배치)
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onDayClick: (LocalDate) -> Unit,
    onEditBaseTime: () -> Unit,
    onOpenAlarm: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenLights: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = ScreenPadding),
    ) {
        if (state.ringing) RingingBanner(onOpenAlarm)

        Hero(state, onOpenRules)

        Spacer(Modifier.height(32.dp))
        SectionLabel("이번 주 · 다음 주")
        if (state.days.isEmpty()) {
            Loading()
        } else {
            TwoWeeks(state.days, state.today, onDayClick)
            Legend()
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.next?.let { next ->
                Button(
                    onClick = { onDayClick(next.date) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Sun, contentColor = Palette.SunInk),
                ) { Text("${next.date.shortRelative(state.today)}만 바꾸기") }
            }
            OutlinedButton(onClick = onEditBaseTime) {
                Text("매일 ${state.baseTime.hhmm()}", color = Palette.Ink)
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel("준비 상태")
        ReadinessList(state.readiness, state.nowMillis, onOpenPermissions, onOpenRules, onOpenLights)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RingingBanner(onOpen: () -> Unit) {
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Palette.SunSoft)
            .clickable(onClick = onOpen)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("알람이 울리고 있어요", style = MaterialTheme.typography.bodyLarge, color = Palette.SunText, modifier = Modifier.weight(1f))
        Text("열기", style = MaterialTheme.typography.labelLarge, color = Palette.SunText)
    }
}

@Composable
private fun Hero(state: HomeUiState, onOpenRules: () -> Unit) {
    val next = state.next
    Column(Modifier.padding(top = 32.dp)) {
        if (next == null) {
            Text("다음 알람", style = MaterialTheme.typography.labelLarge, color = Palette.Mist)
            Spacer(Modifier.height(8.dp))
            Text("예정된 알람이 없어요", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            Text(
                "앞으로 두 달 동안 모두 쉬는 날로 판단했어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onOpenRules, modifier = Modifier.offset(x = (-12).dp)) {
                Text("휴무 규칙 보기", color = Palette.SunText)
            }
        } else {
            Text(
                "${next.date.shortRelative(state.today)} 아침",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Mist,
            )
            Text(
                next.time.hhmm(),
                style = MaterialTheme.typography.displayLarge,
                color = Palette.Ink,
                modifier = Modifier.offset(x = (-4).dp),
            )
            // 남은 시간 (1분마다 갱신)
            Text(
                "${Countdown.text(next.ringAt - state.nowMillis)} 뒤 울려요",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Ink,
            )
            Text(
                "${next.date.longKo()}, ${next.reason}",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Mist,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (state.dimming) {
                Text(
                    "조명을 서서히 켜는 중",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.SunText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Loading() {
    Row(
        Modifier.fillMaxWidth().height(176.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = Palette.Mist, strokeWidth = 2.dp)
        Text("캘린더 확인 중", style = MaterialTheme.typography.bodyMedium, color = Palette.Mist, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * 이번 주·다음 주 달력 (월~일 고정 열). 해(울림)·달(쉼)은 모양(채움/테두리)과 글자로도 구분.
 * 지난 날은 흐리게, 오늘은 칸 배경·테두리로 표시
 */
@Composable
private fun TwoWeeks(days: List<DayCell>, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
    val weekStart = today.with(DayOfWeek.MONDAY)
    val byDate = days.associateBy { it.date }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 요일 머리글
        Row(Modifier.fillMaxWidth()) {
            (0L until 7L).forEach { i ->
                val d = weekStart.plusDays(i)
                Text(
                    d.weekdayShort(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d.dayOfWeek == today.dayOfWeek) Palette.Ink else Palette.Mist,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(2) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (0L until 7L).forEach { i ->
                    val date = weekStart.plusDays(week * 7L + i)
                    val cell = byDate[date]
                    val mod = Modifier.weight(1f)
                    if (date.isBefore(today) || cell == null) {
                        PastDay(date, mod)
                    } else {
                        DayDisc(cell, isToday = date == today, modifier = mod, onClick = { onDayClick(date) })
                    }
                }
            }
        }
    }
}

/** 지난 날: 원 없이 흐린 숫자 (누를 수 없음) */
@Composable
private fun PastDay(date: LocalDate, modifier: Modifier) {
    Column(modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = Palette.Faint)
        }
        Spacer(Modifier.height(4.dp))
        Text("", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DayDisc(d: DayCell, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // 오늘 칸: 다른 배경 + 테두리
    val cellBg = if (isToday) Palette.DuskHigh else Palette.Night
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .then(
                if (isToday) {
                    Modifier
                        .background(cellBg)
                        .border(2.dp, Palette.Ink, MaterialTheme.shapes.small)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val disc = Modifier.size(40.dp).clip(CircleShape)
            if (d.ring) {
                // 밝은 모드에서도 경계가 보이도록 진한 테두리 (다크에선 같은 색)
                Box(disc.background(Palette.Sun).border(2.dp, Palette.SunText, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = Palette.SunInk)
                }
            } else {
                Box(disc.border(2.dp, Palette.Moon, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = Palette.Moon)
                }
            }
            if (d.overridden) {
                // 시각을 바꾼 날: 오른쪽 위 점 (칸 배경색으로 테두리)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(cellBg)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Palette.Ink),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            d.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (d.ring) Palette.Ink else Palette.Mist,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Legend() {
    Row(Modifier.padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Palette.Sun).border(2.dp, Palette.SunText, CircleShape))
        LegendText("울림")
        Box(Modifier.size(12.dp).border(2.dp, Palette.Moon, CircleShape))
        LegendText("쉼")
        Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Ink))
        LegendText("시각 바꾼 날")
        Box(
            Modifier
                .size(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(Palette.DuskHigh)
                .border(1.5.dp, Palette.Ink, MaterialTheme.shapes.extraSmall),
        )
        LegendText("오늘")
    }
}

@Composable
private fun LegendText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = Modifier.padding(start = 8.dp, end = 16.dp))
}

@Composable
private fun ReadinessList(
    r: Readiness,
    now: Long,
    onOpenPermissions: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenLights: () -> Unit,
) {
    Column {
        // 시스템 상태 가시성: 앱이 실제로 일정을 확인했는지, 지난 알람이 어떻게 끝났는지
        r.lastCheck?.let { at ->
            if (r.lastCheckCalendarOk) {
                StatusLine(true, "일정 확인: ${agoKo(at, now)}")
            } else {
                StatusLine(false, "${agoKo(at, now)} 일정 확인 때 캘린더를 못 읽었어요", "확인", onOpenPermissions)
            }
        }
        if (r.lastResult != null && r.lastResultAt != null) {
            StatusLine(true, "지난 알람: ${r.lastResult} (${agoKo(r.lastResultAt, now)})")
        }
        if (r.missingPermissions > 0) {
            StatusLine(false, "권한 ${r.missingPermissions}개가 필요해요", "설정", onOpenPermissions)
        } else {
            StatusLine(true, "권한 준비됨")
        }
        when {
            !r.calendarReadable -> StatusLine(false, "캘린더를 읽지 못해 매일 울려요", "확인", onOpenPermissions)
            !r.holidayCalendarSet -> StatusLine(false, "공휴일 캘린더를 골라주세요", "선택", onOpenRules)
            else -> StatusLine(true, "공휴일 캘린더 연결됨")
        }
        when (r.watchNodes) {
            null -> StatusLine(null, "워치 확인 중")
            0 -> StatusLine(null, "워치 연결 안 됨, 폰 걸음만 세요")
            else -> StatusLine(true, "워치 연결됨")
        }
        when {
            r.lightsAuthError && r.lightsLinked != null ->
                StatusLine(false, "조명 로그인이 만료됐어요", "다시 로그인", onOpenLights)
            r.lightsLinked == null -> StatusLine(null, "조명 연동 안 함")
            r.lightsLinked == 0 -> StatusLine(null, "조명 계정 연결됨, 기기를 골라주세요", "선택", onOpenLights)
            else -> StatusLine(true, "조명 ${r.lightsLinked}개 연결됨")
        }
    }
}
