package io.github.leepy0.strongalarm.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.core.DayOffRule
import io.github.leepy0.strongalarm.ui.components.ChipTone
import io.github.leepy0.strongalarm.ui.components.Group
import io.github.leepy0.strongalarm.ui.components.GroupDivider
import io.github.leepy0.strongalarm.ui.components.KeywordChips
import io.github.leepy0.strongalarm.ui.components.OutcomeTag
import io.github.leepy0.strongalarm.ui.components.RowItem
import io.github.leepy0.strongalarm.ui.components.ScreenPadding
import io.github.leepy0.strongalarm.ui.components.SectionLabel
import io.github.leepy0.strongalarm.ui.theme.Palette

data class RulesUiState(
    val rule: DayOffRule,
    val calendarNames: Map<Long, String>,
    val overrideCount: Int,
    val calendarReadable: Boolean,
)

@Composable
fun RulesScreen(
    state: RulesUiState,
    onChange: (DayOffRule) -> Unit,
    onPickHolidayCalendars: () -> Unit,
    onPickTargetCalendars: () -> Unit,
    onGrantCalendar: () -> Unit,
) {
    val rule = state.rule
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = ScreenPadding),
    ) {
        Text(
            "휴무 규칙",
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.Ink,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            "위에서부터 먼저 맞는 규칙 하나로 그날 울릴지 정해요.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Mist,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        if (!state.calendarReadable) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Dusk)
                    .clickable(onClick = onGrantCalendar)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "캘린더 권한이 없어 지금은 매일 울려요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ember,
                    modifier = Modifier.weight(1f),
                )
                Text("허용", style = MaterialTheme.typography.labelLarge, color = Palette.Sun)
            }
        }

        Step(1, "날짜별 시각 변경", ring = true) {
            Text(
                if (state.overrideCount > 0) {
                    "알람 탭에서 날짜를 눌러 바꿔요. 지금 ${state.overrideCount}일 바꿔뒀어요."
                } else {
                    "알람 탭에서 날짜를 눌러 그날만 시각을 바꿔요."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Mist,
            )
        }
        Step(2, "근무 일정", ring = true) {
            StepHint("일정 제목에 이 단어가 있으면 주말·공휴일에도 울려요")
            KeywordChips(rule.workKeywords, ChipTone.SUN) { onChange(rule.copy(workKeywords = it)) }
        }
        Step(3, "휴무 일정", ring = false) {
            StepHint("일정 제목에 이 단어가 있으면 쉬어요")
            KeywordChips(rule.offKeywords, ChipTone.MOON) { onChange(rule.copy(offKeywords = it)) }
            Spacer(Modifier.height(14.dp))
            StepHint("이 단어가 함께 있으면 휴무로 보지 않아요")
            KeywordChips(rule.excludeKeywords, ChipTone.PLAIN) { onChange(rule.copy(excludeKeywords = it)) }
        }
        Step(4, "공휴일", ring = false) {
            val names = rule.holidayCalendarIds.mapNotNull { state.calendarNames[it] }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickHolidayCalendars)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (rule.holidayCalendarIds.isEmpty()) "공휴일 캘린더를 골라주세요" else names.ifEmpty { listOf("선택한 캘린더") }.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rule.holidayCalendarIds.isEmpty()) Palette.Ember else Palette.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text("변경", style = MaterialTheme.typography.labelLarge, color = Palette.Sun)
            }
            Spacer(Modifier.height(10.dp))
            StepHint("공휴일 캘린더에 있어도 무시할 기념일")
            KeywordChips(rule.holidayExcludeKeywords, ChipTone.PLAIN) { onChange(rule.copy(holidayExcludeKeywords = it)) }
        }
        Step(5, "주말", ring = false, last = true) {
            StepHint("토요일과 일요일")
        }
        Text(
            "어디에도 해당하지 않는 평일은 울려요.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Mist,
            modifier = Modifier.padding(start = 40.dp, top = 4.dp),
        )

        Spacer(Modifier.height(36.dp))
        SectionLabel("일정을 읽을 캘린더")
        Group {
            val target = if (rule.calendarIds.isEmpty()) {
                "전체"
            } else {
                rule.calendarIds.mapNotNull { state.calendarNames[it] }.joinToString(", ").ifEmpty { "${rule.calendarIds.size}개" }
            }
            RowItem(
                title = "근무·휴무 일정을 찾을 캘린더",
                subtitle = "공유 캘린더의 다른 사람 일정은 빼두세요",
                value = target,
                onClick = onPickTargetCalendars,
            )
            GroupDivider()
            RowItem(
                title = "종일 일정만 보기",
                subtitle = "시간이 정해진 회의 등은 무시해요",
                trailing = {
                    Switch(
                        checked = rule.allDayOnly,
                        onCheckedChange = { onChange(rule.copy(allDayOnly = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Palette.SunInk,
                            checkedTrackColor = Palette.Sun,
                            uncheckedThumbColor = Palette.Mist,
                            uncheckedTrackColor = Palette.Night,
                            uncheckedBorderColor = Palette.Line,
                        ),
                    )
                },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** 우선순위 사다리의 한 단: 번호 원 + 연결선 + 내용 */
@Composable
private fun Step(
    order: Int,
    title: String,
    ring: Boolean,
    last: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!last) {
                    val x = 13.dp.toPx()
                    drawLine(Palette.Line, Offset(x, 30.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                }
            },
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Palette.DuskHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text("$order", style = MaterialTheme.typography.labelMedium, color = Palette.Ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 8.dp else 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Spacer(Modifier.width(8.dp))
                OutcomeTag(ring)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StepHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Mist, modifier = Modifier.padding(bottom = 10.dp))
}
