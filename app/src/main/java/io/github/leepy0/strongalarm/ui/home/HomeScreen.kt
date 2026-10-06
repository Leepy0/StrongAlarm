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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.ui.components.ScreenPadding
import io.github.leepy0.strongalarm.ui.components.SectionLabel
import io.github.leepy0.strongalarm.ui.components.StatusLine
import io.github.leepy0.strongalarm.ui.hhmm
import io.github.leepy0.strongalarm.ui.longKo
import io.github.leepy0.strongalarm.ui.relativeKo
import io.github.leepy0.strongalarm.ui.theme.Palette
import io.github.leepy0.strongalarm.ui.weekdayShort
import java.time.LocalDate
import java.time.LocalTime

/** 2주 스트립의 하루 */
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

data class NextAlarm(val date: LocalDate, val time: LocalTime, val reason: String)

data class Readiness(
    val missingPermissions: Int,
    /** null = 확인 중 */
    val watchNodes: Int?,
    /** null = 조명 미사용 */
    val lightsLinked: Int?,
    val holidayCalendarSet: Boolean,
    val calendarReadable: Boolean,
)

data class HomeUiState(
    val today: LocalDate,
    val next: NextAlarm?,
    val baseTime: LocalTime,
    val days: List<DayCell>,
    val ringing: Boolean,
    val dimming: Boolean,
    val readiness: Readiness,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onDayClick: (LocalDate) -> Unit,
    onEditBaseTime: () -> Unit,
    onOpenAlarm: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenRules: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = ScreenPadding),
    ) {
        if (state.ringing) {
            RingingBanner(onOpenAlarm)
        }

        Hero(state, onDayClick, onEditBaseTime)

        Spacer(Modifier.height(40.dp))
        SectionLabel("앞으로 2주")
        TwoWeeks(state.days, state.today, onDayClick)
        Legend()

        Spacer(Modifier.height(36.dp))
        SectionLabel("준비 상태")
        ReadinessList(state.readiness, onOpenPermissions, onOpenRules)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RingingBanner(onOpen: () -> Unit) {
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SunSoft)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("알람이 울리고 있어요", style = MaterialTheme.typography.bodyLarge, color = Palette.Sun, modifier = Modifier.weight(1f))
        Text("열기", style = MaterialTheme.typography.labelLarge, color = Palette.Sun)
    }
}

@Composable
private fun Hero(state: HomeUiState, onDayClick: (LocalDate) -> Unit, onEditBaseTime: () -> Unit) {
    val next = state.next
    Column(Modifier.padding(top = 36.dp)) {
        if (next == null) {
            Text("다음 알람", style = MaterialTheme.typography.titleMedium, color = Palette.Mist)
            Spacer(Modifier.height(8.dp))
            Text("예정된 알람이 없어요", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            Text(
                "앞으로 두 달 동안 모두 쉬는 날로 판단했어요. 휴무 규칙을 확인해보세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Text(
                "${next.date.relativeKo(state.today)} 아침",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Mist,
            )
            Text(
                next.time.hhmm(),
                style = MaterialTheme.typography.displayLarge,
                color = Palette.Ink,
                modifier = Modifier.offset(x = (-4).dp),
            )
            Text(
                "${next.date.longKo()}, ${next.reason}",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Ink,
            )
            if (state.dimming) {
                Text(
                    "조명을 서서히 켜는 중",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Sun,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (next != null) {
                Button(
                    onClick = { onDayClick(next.date) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Sun, contentColor = Palette.SunInk),
                ) { Text("이 날만 바꾸기") }
            }
            OutlinedButton(onClick = onEditBaseTime) {
                Text("매일 ${state.baseTime.hhmm()}", color = Palette.Ink)
            }
        }
    }
}

/** 해(울림) · 달(쉼) 2주 격자 */
@Composable
private fun TwoWeeks(days: List<DayCell>, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    DayDisc(d, isToday = d.date == today, modifier = Modifier.weight(1f), onClick = { onDayClick(d.date) })
                }
                // 마지막 주가 7일 미만이면 빈 칸 채움
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayDisc(d: DayCell, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (isToday) "오늘" else d.date.weekdayShort(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) Palette.Ink else Palette.Mist,
            fontWeight = if (isToday) FontWeight.Bold else null,
        )
        Spacer(Modifier.height(6.dp))
        Box(contentAlignment = Alignment.Center) {
            val discModifier = Modifier.size(38.dp).clip(CircleShape)
            if (d.ring) {
                Box(discModifier.background(Palette.Sun), contentAlignment = Alignment.Center) {
                    Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = Palette.SunInk)
                }
            } else {
                Box(
                    discModifier.border(1.5.dp, Palette.Moon.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = Palette.Moon)
                }
            }
            if (d.overridden) {
                // 시각을 바꾼 날 표시
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-1).dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Palette.Night)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Palette.Ink),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
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
    Row(Modifier.padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Sun))
        Text("울림", style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = Modifier.padding(start = 6.dp, end = 16.dp))
        Box(Modifier.size(10.dp).border(1.5.dp, Palette.Moon, CircleShape))
        Text("쉼", style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = Modifier.padding(start = 6.dp, end = 16.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Ink))
        Text("시각 바꾼 날", style = MaterialTheme.typography.labelSmall, color = Palette.Mist, modifier = Modifier.padding(start = 6.dp))
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun ReadinessList(r: Readiness, onOpenPermissions: () -> Unit, onOpenRules: () -> Unit) {
    Column {
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
        when (r.lightsLinked) {
            null -> StatusLine(null, "조명 연동 안 함")
            0 -> StatusLine(null, "조명 계정 연결됨, 기기를 골라주세요")
            else -> StatusLine(true, "조명 ${r.lightsLinked}개 연결됨")
        }
    }
}
