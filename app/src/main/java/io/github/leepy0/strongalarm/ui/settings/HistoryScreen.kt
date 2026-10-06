package io.github.leepy0.strongalarm.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.ui.components.Group
import io.github.leepy0.strongalarm.ui.components.GroupDivider
import io.github.leepy0.strongalarm.ui.components.SectionLabel
import io.github.leepy0.strongalarm.ui.components.Subpage
import io.github.leepy0.strongalarm.ui.theme.Palette

/** "MM-dd HH:mm:ss | 메시지" 줄을 날짜별로 묶어 최신순 표시 */
@Composable
fun HistoryScreen(lines: List<String>, onBack: () -> Unit, onTestAlarm: () -> Unit) {
    Subpage("기록", onBack) {
        if (lines.isEmpty()) {
            // 빈 상태: 이유 + 바로 할 수 있는 행동
            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("아직 기록이 없어요", style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
                Text(
                    "알람이 울리고 꺼진 과정, 조명 제어 결과가 여기에 남아요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Mist,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(onClick = onTestAlarm, modifier = Modifier.padding(top = 24.dp)) {
                    Text("10초 뒤 테스트 알람", color = Palette.Ink)
                }
            }
        }
        val entries = lines.reversed().map { line ->
            val stamp = line.substringBefore(" | ", "")
            val msg = line.substringAfter(" | ", line)
            Triple(stamp.substringBefore(' ', ""), stamp.substringAfter(' ', "").take(5), msg)
        }
        entries.groupBy { it.first }.forEach { (day, items) ->
            Column {
                SectionLabel(day.replace('-', '/').ifEmpty { "날짜 없음" })
                Group {
                    items.forEachIndexed { i, (_, time, msg) ->
                        if (i > 0) GroupDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(time, style = MaterialTheme.typography.labelMedium, color = Palette.Mist, modifier = Modifier.width(48.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
                        }
                    }
                }
            }
        }
    }
}
