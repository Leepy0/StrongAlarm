package io.github.leepy0.strongalarm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.ui.components.Group
import io.github.leepy0.strongalarm.ui.components.GroupDivider
import io.github.leepy0.strongalarm.ui.components.RowItem
import io.github.leepy0.strongalarm.ui.components.SectionLabel
import io.github.leepy0.strongalarm.ui.components.Subpage
import io.github.leepy0.strongalarm.ui.theme.Palette

data class LightsUiState(
    val clientId: String,
    val redirectUri: String,
    val hasSecret: Boolean,
    val loggedIn: Boolean,
    val dimmers: List<String>,
    val switches: List<String>,
    val leadMinutes: Int,
    val switchDelayMinutes: Int,
    val autoOffMinutes: Int,
    /** 조명 테스트 중 */
    val busy: Boolean,
    /** 기기 목록 불러오는 중 */
    val loadingDevices: Boolean = false,
)

@Composable
fun LightsScreen(
    state: LightsUiState,
    onBack: () -> Unit,
    onSaveCredentials: (clientId: String, secret: String, redirectUri: String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onPickDimmers: () -> Unit,
    onPickSwitches: () -> Unit,
    onTest: () -> Unit,
) {
    var editing by remember(state.loggedIn) { mutableStateOf(!state.loggedIn) }

    Subpage("조명", onBack) {
        // 실제 시간 순서라 번호를 붙임
        Column {
            SectionLabel("알람 날 조명 흐름")
            Group {
                FlowStep(1, "알람 ${state.leadMinutes}분 전부터", "디밍 조명을 1%에서 100%까지 서서히 켜요")
                GroupDivider()
                FlowStep(2, "울린 뒤 ${state.switchDelayMinutes}분 동안 안 끄면", "스위치를 켜요")
                GroupDivider()
                FlowStep(3, "알람을 끄고 ${state.autoOffMinutes}분 뒤", "앱이 켠 조명만 꺼요. 쉬는 날 버튼이면 바로 원래대로 돌려요.")
            }
        }

        if (state.loggedIn) {
            Column {
                SectionLabel("기기")
                Group {
                    if (state.loadingDevices) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.SunText, trackColor = Palette.Line)
                    }
                    RowItem(
                        title = "디밍 조명",
                        subtitle = state.dimmers.joinToString(", ").ifEmpty { "밝기 조절되는 조명을 골라주세요" },
                        onClick = onPickDimmers,
                    )
                    GroupDivider()
                    RowItem(
                        title = "스위치",
                        subtitle = state.switches.joinToString(", ").ifEmpty { "안 끄면 켤 스위치를 골라주세요" },
                        onClick = onPickSwitches,
                    )
                }
                OutlinedButton(
                    onClick = onTest,
                    enabled = !state.busy && state.dimmers.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Palette.Mist, strokeWidth = 2.dp)
                        Text("테스트 중, 곧 원래대로 돌아가요", color = Palette.Mist, modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Text("디밍 조명 테스트", color = Palette.Ink)
                    }
                }
            }
        }

        Column {
            SectionLabel("SmartThings 계정")
            Row(Modifier.padding(start = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state.loggedIn) Palette.SunText else Palette.Mist),
                )
                Text(
                    if (state.loggedIn) "연결됨" else "연결 안 됨",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ink,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (editing) {
                CredentialsForm(state, onSaveCredentials, onLogin)
            } else {
                // 위험 동작(연결 끊기)은 일반 동작과 떨어뜨려 배치
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { editing = true }) { Text("연결 정보 수정", color = Palette.Ink) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onLogout) { Text("연결 끊기", color = Palette.Ember) }
                }
            }
        }
    }
}

@Composable
private fun FlowStep(order: Int, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "$order",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.SunText,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Mist)
        }
    }
}

@Composable
private fun CredentialsForm(
    state: LightsUiState,
    onSave: (String, String, String) -> Unit,
    onLogin: () -> Unit,
) {
    var clientId by remember(state.clientId) { mutableStateOf(state.clientId) }
    var secret by remember { mutableStateOf("") }
    var redirect by remember(state.redirectUri) { mutableStateOf(state.redirectUri) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Palette.SunText,
        unfocusedBorderColor = Palette.Line,
        focusedLabelColor = Palette.SunText,
        cursorColor = Palette.SunText,
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Palette.Dusk)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "PC에서 SmartThings CLI로 OAuth 앱을 만들고(권한 r:devices:*, x:devices:*) 받은 값을 넣어주세요. 자세한 방법은 README에 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Mist,
        )
        OutlinedTextField(
            clientId, { clientId = it }, Modifier.fillMaxWidth(),
            label = { Text("Client ID") }, singleLine = true, colors = fieldColors,
        )
        OutlinedTextField(
            secret, { secret = it }, Modifier.fillMaxWidth(),
            label = { Text(if (state.hasSecret) "Client Secret (저장됨, 바꿀 때만 입력)" else "Client Secret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = fieldColors,
        )
        OutlinedTextField(
            redirect, { redirect = it }, Modifier.fillMaxWidth(),
            label = { Text("Redirect URI") }, singleLine = true, colors = fieldColors,
        )
        val canLogin = clientId.isNotBlank() && (state.hasSecret || secret.isNotBlank())
        Button(
            onClick = {
                onSave(clientId.trim(), secret, redirect.trim())
                secret = ""
                onLogin()
            },
            enabled = canLogin,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Sun, contentColor = Palette.SunInk),
        ) { Text("저장하고 로그인") }
    }
}
