package io.github.leepy0.strongalarm.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.ui.components.ScreenPadding
import io.github.leepy0.strongalarm.ui.hhmm
import io.github.leepy0.strongalarm.ui.longKo
import io.github.leepy0.strongalarm.ui.theme.Palette
import java.time.LocalTime

/** 날짜를 눌렀을 때: 판정 결과 + 그날만 바꾸기. 주요 행동 1개(쉬는 날이면 '울리기', 아니면 '다른 시각') */
@Composable
fun DaySheetContent(
    day: DayCell,
    baseTime: LocalTime,
    onRingAnyway: () -> Unit,
    onPickTime: () -> Unit,
    onClearOverride: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 32.dp)) {
        Text(day.date.longKo(), style = MaterialTheme.typography.titleMedium, color = Palette.Mist)
        Spacer(Modifier.height(8.dp))
        Text(
            if (day.ring) "${day.time.hhmm()}에 울려요" else "울리지 않아요",
            style = MaterialTheme.typography.headlineSmall,
            color = if (day.ring) Palette.SunText else Palette.Moon,
        )
        Text(day.reason, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!day.ring) {
                Button(
                    onClick = onRingAnyway,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Sun, contentColor = Palette.SunInk),
                ) { Text("이 날은 ${baseTime.hhmm()}에 울리기") }
                OutlinedButton(onClick = onPickTime, modifier = Modifier.fillMaxWidth()) {
                    Text("다른 시각에 울리기", color = Palette.Ink)
                }
            } else {
                Button(
                    onClick = onPickTime,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Sun, contentColor = Palette.SunInk),
                ) { Text(if (day.overridden) "시각 다시 바꾸기" else "이 날만 다른 시각으로") }
            }
            if (day.overridden) {
                TextButton(onClick = onClearOverride, modifier = Modifier.fillMaxWidth()) {
                    Text("바꾼 시각 취소", color = Palette.Mist)
                }
            }
        }
    }
}
