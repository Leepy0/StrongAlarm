package io.github.leepy0.strongalarm.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.R
import io.github.leepy0.strongalarm.ui.components.AppIcon
import io.github.leepy0.strongalarm.ui.components.ScreenPadding
import io.github.leepy0.strongalarm.ui.hhmm
import io.github.leepy0.strongalarm.ui.longKo
import io.github.leepy0.strongalarm.ui.theme.Palette
import java.time.LocalTime

/**
 * 날짜를 눌렀을 때: 판정 결과 + 그날만 바꾸기. 주요 행동은 1개만 채움 버튼
 * - 울리는 날: (다음 알람이면) 울림 확인 / 시각 변경 / 쉬는 날로
 * - 쉬는 날: 울리기 / 다른 시각 / (직접 지정했으면) 지정 취소
 * @param confirmable 다음 알람 날짜라 울림 확인 가능
 * @param locked 알람이 진행 중이라 쉬는 날 지정·확인 취소 불가
 */
@Composable
fun DaySheetContent(
    day: DayCell,
    baseTime: LocalTime,
    onRingAnyway: () -> Unit,
    onPickTime: () -> Unit,
    onClearOverride: () -> Unit,
    confirmable: Boolean = false,
    locked: Boolean = false,
    onSkip: () -> Unit = {},
    onClearSkip: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onUnconfirm: () -> Unit = {},
) {
    val sunButton = ButtonDefaults.buttonColors(containerColor = Palette.Sun, contentColor = Palette.SunInk)
    Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 32.dp)) {
        Text(day.date.longKo(), style = MaterialTheme.typography.titleMedium, color = Palette.Mist)
        Spacer(Modifier.height(8.dp))
        Text(
            if (day.ring) "${day.time.hhmm()}에 울려요" else "울리지 않아요",
            style = MaterialTheme.typography.headlineSmall,
            color = if (day.ring) Palette.SunText else Palette.Moon,
        )
        Text(day.reason, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.padding(top = 4.dp))
        if (day.ring && day.confirmed) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_check, Palette.Ink, size = 20)
                Text(
                    "울림 확인됨 · 쉬는 날 버튼 없이 걸어야만 꺼져요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ink,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (locked) {
            Text(
                "알람이 진행 중이라 쉬는 날로 바꿀 수 없어요",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (day.ring) {
                val showConfirm = confirmable && !day.confirmed
                if (showConfirm) {
                    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth(), colors = sunButton) {
                        Text("울림 확인 (아침에 못 끄게)")
                    }
                }
                val timeLabel = if (day.overridden) "시각 다시 바꾸기" else "이 날만 다른 시각으로"
                if (showConfirm) {
                    OutlinedButton(onClick = onPickTime, modifier = Modifier.fillMaxWidth()) { Text(timeLabel, color = Palette.Ink) }
                } else {
                    Button(onClick = onPickTime, modifier = Modifier.fillMaxWidth(), colors = sunButton) { Text(timeLabel) }
                }
                OutlinedButton(onClick = onSkip, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                    Text("쉬는 날로 바꾸기", color = if (locked) Palette.Faint else Palette.Moon)
                }
                if (day.overridden) {
                    TextButton(onClick = onClearOverride, modifier = Modifier.fillMaxWidth()) {
                        Text("바꾼 시각 취소", color = Palette.Mist)
                    }
                }
                if (day.confirmed && !locked) {
                    TextButton(onClick = onUnconfirm, modifier = Modifier.fillMaxWidth()) {
                        Text("울림 확인 취소", color = Palette.Mist)
                    }
                }
            } else {
                Button(onClick = onRingAnyway, modifier = Modifier.fillMaxWidth(), colors = sunButton) {
                    Text("이 날은 ${baseTime.hhmm()}에 울리기")
                }
                OutlinedButton(onClick = onPickTime, modifier = Modifier.fillMaxWidth()) {
                    Text("다른 시각에 울리기", color = Palette.Ink)
                }
                if (day.manualOff) {
                    TextButton(onClick = onClearSkip, modifier = Modifier.fillMaxWidth()) {
                        Text("쉬는 날 지정 취소", color = Palette.Mist)
                    }
                }
            }
        }
    }
}
