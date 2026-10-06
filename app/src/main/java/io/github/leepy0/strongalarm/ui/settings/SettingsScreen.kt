package io.github.leepy0.strongalarm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.R
import io.github.leepy0.strongalarm.ui.components.AppIcon
import io.github.leepy0.strongalarm.ui.components.Group
import io.github.leepy0.strongalarm.ui.components.GroupDivider
import io.github.leepy0.strongalarm.ui.components.RowItem
import io.github.leepy0.strongalarm.ui.components.ScreenPadding
import io.github.leepy0.strongalarm.ui.components.SectionLabel
import io.github.leepy0.strongalarm.ui.theme.Palette

data class SettingsUiState(
    val stepGoal: Int,
    /** null = 조명 미사용 */
    val lightsSummary: String?,
    val missingPermissions: Int,
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onStepGoal: (Int) -> Unit,
    onOpenLights: () -> Unit,
    onOpenPermissions: () -> Unit,
    onTestAlarm: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(
            "설정",
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.Ink,
            modifier = Modifier.padding(top = 28.dp),
        )

        Column {
            SectionLabel("알람 끄기")
            Group {
                RowItem(
                    title = "걸음 수",
                    subtitle = "워치나 폰으로 이만큼 걸어야 꺼져요",
                    trailing = { Stepper(state.stepGoal, onStepGoal) },
                )
                GroupDivider()
                RowItem(
                    title = "쉬는 날 버튼",
                    subtitle = "알람 화면에서 5초 누르면 바로 꺼져요. 누르는 동안은 소리가 멈춰요.",
                )
            }
        }

        Column {
            SectionLabel("연결")
            Group {
                RowItem(
                    title = "조명",
                    subtitle = "SmartThings",
                    value = state.lightsSummary ?: "사용 안 함",
                    onClick = onOpenLights,
                )
                GroupDivider()
                RowItem(
                    title = "권한과 기기 설정",
                    value = if (state.missingPermissions > 0) "${state.missingPermissions}개 필요" else "준비됨",
                    valueColor = if (state.missingPermissions > 0) Palette.Ember else Palette.Mist,
                    onClick = onOpenPermissions,
                )
            }
        }

        Column {
            SectionLabel("확인")
            Group {
                RowItem(
                    title = "10초 뒤 테스트 알람",
                    subtitle = "화면을 끄고 기다려보세요. 조명은 켜지 않아요.",
                    onClick = onTestAlarm,
                )
                GroupDivider()
                RowItem(title = "기록", subtitle = "판정, 울림, 해제, 조명 동작", onClick = onOpenHistory)
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((value - 5).coerceAtLeast(10)) }) {
            AppIcon(R.drawable.ic_minus, Palette.Ink, size = 18)
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        IconButton(onClick = { onChange((value + 5).coerceAtMost(200)) }) {
            AppIcon(R.drawable.ic_plus, Palette.Ink, size = 18)
        }
    }
}
