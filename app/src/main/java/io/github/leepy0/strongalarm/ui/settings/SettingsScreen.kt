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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlin.math.roundToInt

data class SettingsUiState(
    val stepGoal: Int,
    /** 알람 크기 % (10~100) */
    val alarmVolume: Int = 100,
    /** 미리 듣기 재생 중 */
    val previewing: Boolean = false,
    val app: AppVersionUi = AppVersionUi("0.2.1"),
    /** null = 조명 미사용 */
    val lightsSummary: String?,
    val missingPermissions: Int,
)

/** 앱 버전·업데이트 상태 */
data class AppVersionUi(
    val installed: String,
    /** 새 버전 이름. null = 없음 */
    val newVersion: String? = null,
    /** 설치된 버전 이후 변경 내역 (최신순) */
    val notes: List<String> = emptyList(),
    /** 확인 상태 문장. 예: "최신이에요 · 5분 전 확인" */
    val status: String = "",
    val checking: Boolean = false,
    /** 새 버전에서 워치 앱도 바뀜 */
    val watchChanged: Boolean = false,
    /** APK 다운로드 진행 (없으면 null) */
    val download: DownloadUi? = null,
)

data class DownloadUi(
    /** "폰" / "워치" */
    val label: String,
    /** 0~1, 크기 모르면 null */
    val fraction: Float?,
    val done: Boolean,
    /** 실패 사유. null = 실패 아님 */
    val failed: String? = null,
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onStepGoal: (Int) -> Unit,
    onVolume: (Int) -> Unit,
    onPreview: () -> Unit,
    onOpenLights: () -> Unit,
    onOpenPermissions: () -> Unit,
    onTestAlarm: () -> Unit,
    onOpenHistory: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadPhone: () -> Unit,
    onDownloadWatch: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    onOpenReleasePage: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Text(
            "설정",
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.Ink,
            modifier = Modifier.padding(top = 32.dp),
        )

        Column {
            SectionLabel("소리")
            Group { VolumeRow(state.alarmVolume, state.previewing, onVolume, onPreview) }
        }

        Column {
            SectionLabel("알람 끄기")
            Group {
                RowItem(
                    title = "걸음 수",
                    subtitle = "이만큼 걸어야 꺼져요",
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

        Column(Modifier.padding(bottom = 24.dp)) {
            SectionLabel("앱")
            Group { AppVersionRows(state.app, onCheckUpdate, onDownloadPhone, onDownloadWatch, onOpenDownloads, onOpenReleasePage) }
        }
    }
}

/** 버전·새 버전 안내. 설치는 시스템 다운로드 → 다운로드 알림·목록에서 APK를 눌러 시스템 설치 화면 */
@Composable
private fun AppVersionRows(
    app: AppVersionUi,
    onCheck: () -> Unit,
    onDownloadPhone: () -> Unit,
    onDownloadWatch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    if (app.newVersion == null) {
        RowItem(
            title = "버전 ${app.installed}",
            subtitle = app.status,
            trailing = {
                if (app.checking) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), color = Palette.Mist, strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onCheck) { Text("확인", color = Palette.Ink) }
                }
            },
        )
        return
    }
    val busy = app.download?.let { !it.done && it.failed == null } == true
    RowItem(
        title = "새 버전 ${app.newVersion}",
        subtitle = "지금 ${app.installed} · 설정은 그대로 남아요",
        trailing = {
            Button(
                onClick = onDownloadPhone,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Sun, contentColor = Palette.SunInk),
                modifier = Modifier.padding(end = 8.dp),
            ) { Text("받기") }
        },
    )
    app.download?.let { DownloadBlock(it, onOpenDownloads) }
    if (app.notes.isNotEmpty()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            app.notes.take(5).forEach { n ->
                Text("· $n", style = MaterialTheme.typography.bodySmall, color = Palette.Mist)
            }
            if (app.notes.size > 5) {
                Text("외 ${app.notes.size - 5}건", style = MaterialTheme.typography.bodySmall, color = Palette.Mist)
            }
        }
    }
    Row(
        Modifier.padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "다 받으면 다운로드 알림을 눌러 설치해요.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Mist,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenReleasePage) { Text("웹에서 받기", color = Palette.Mist) }
    }
    if (app.watchChanged) {
        GroupDivider()
        RowItem(
            title = "워치 앱도 바뀌었어요",
            subtitle = "워치 APK를 받아 Wear Installer로 설치해주세요",
            onClick = onDownloadWatch,
        )
    }
}

/** 다운로드 진행·완료·실패 표시 */
@Composable
private fun DownloadBlock(d: DownloadUi, onOpenDownloads: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        when {
            d.failed != null -> Text("${d.label} APK: ${d.failed}", style = MaterialTheme.typography.bodyMedium, color = Palette.Ember)
            d.done -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${d.label} APK를 다 받았어요. 다운로드 알림이나 목록에서 눌러 설치해요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ink,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onOpenDownloads,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Sun, contentColor = Palette.SunInk),
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("목록 열기") }
            }
            else -> {
                Text(
                    "${d.label} APK 받는 중" + (d.fraction?.let { " ${(it * 100).toInt()}%" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ink,
                )
                val mod = Modifier.fillMaxWidth().padding(top = 8.dp)
                if (d.fraction != null) {
                    LinearProgressIndicator(progress = { d.fraction }, modifier = mod, color = Palette.SunText, trackColor = Palette.Line)
                } else {
                    LinearProgressIndicator(modifier = mod, color = Palette.SunText, trackColor = Palette.Line)
                }
            }
        }
    }
}

/** 알람 크기: 10% 단위 슬라이더 + 미리 듣기. 바꾸는 즉시 저장, 미리 듣는 중이면 바로 반영 */
@Composable
private fun VolumeRow(volume: Int, previewing: Boolean, onVolume: (Int) -> Unit, onPreview: () -> Unit) {
    var v by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("알람 크기", style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                Text(
                    "처음부터 이 크기로 울려요. 끄면 원래 볼륨으로 돌아가요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Mist,
                )
            }
            Text("${v.roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = v,
                onValueChange = {
                    val stepped = (it / 10f).roundToInt() * 10f
                    if (stepped != v) {
                        v = stepped
                        onVolume(stepped.roundToInt())
                    }
                },
                valueRange = 10f..100f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = Palette.Sun,
                    activeTrackColor = Palette.Sun,
                    inactiveTrackColor = Palette.Line,
                    // 10% 단위 눈금 점은 숨김 (값은 오른쪽 위 숫자로 확인)
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPreview, modifier = Modifier.padding(start = 8.dp)) {
                Text(if (previewing) "멈춤" else "미리 듣기", color = Palette.Ink)
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((value - 5).coerceAtLeast(10)) }) {
            AppIcon(R.drawable.ic_minus, Palette.Ink, size = 20)
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        IconButton(onClick = { onChange((value + 5).coerceAtMost(200)) }) {
            AppIcon(R.drawable.ic_plus, Palette.Ink, size = 20)
        }
    }
}
