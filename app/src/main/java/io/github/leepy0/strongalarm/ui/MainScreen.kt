@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leepy0.strongalarm.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leepy0.strongalarm.alarm.AlarmScheduler
import io.github.leepy0.strongalarm.alarm.AlarmSession
import io.github.leepy0.strongalarm.core.Judgement
import io.github.leepy0.strongalarm.data.AppSettings
import io.github.leepy0.strongalarm.data.AppState
import io.github.leepy0.strongalarm.data.CalendarReader
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.SessionState
import io.github.leepy0.strongalarm.data.Phase
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.lights.LightController
import io.github.leepy0.strongalarm.lights.SmartThingsAuth
import io.github.leepy0.strongalarm.lights.SmartThingsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private typealias Save = ((AppSettings) -> AppSettings) -> Unit

@Composable
fun MainScreen(overrideRequest: LocalDate?, onOverrideHandled: () -> Unit) {
    val ctx = LocalContext.current
    val settings by Stores.settings.flow(ctx).collectAsStateWithLifecycle()
    val state by Stores.state.flow(ctx).collectAsStateWithLifecycle()
    val ui by AlarmSession.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 설정 화면 복귀 시 권한 상태 등 갱신
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }

    val save: Save = { f ->
        Stores.settings.update(ctx, f)
        scope.launch(Dispatchers.IO) { AlarmScheduler.rescheduleAll(ctx) }
    }

    // 공휴일 캘린더 자동 선택 (최초 1회, 캘린더 권한 허용 후)
    LaunchedEffect(tick) {
        if (!settings.holidayAutoDetected && CalendarReader.hasPermission(ctx)) {
            val found = withContext(Dispatchers.IO) { CalendarReader.listCalendars(ctx) }
                .filter { it.holidayLike }.map { it.id }.toSet()
            save {
                it.copy(
                    holidayAutoDetected = true,
                    rule = if (it.rule.holidayCalendarIds.isEmpty()) it.rule.copy(holidayCalendarIds = found) else it.rule,
                )
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("강력한 알람") }) }) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(settings, state, ui) }
            item { AlarmTimeCard(settings, save) }
            item { OverrideCard(settings, overrideRequest, onOverrideHandled, save) }
            item { DayOffCard(settings, tick, save) }
            item { LightsCard(settings, tick, save) }
            item { PermissionCard(tick) }
            item { TestCard() }
            item { HistoryCard(tick, state) }
        }
    }
}

@Composable
private fun StatusCard(settings: AppSettings, state: AppState, ui: AlarmSession.UiState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    SectionCard("상태") {
        when (ui.phase) {
            AlarmSession.UiPhase.RINGING, AlarmSession.UiPhase.PAUSED -> {
                Text("알람 울리는 중 · 걸음 ${ui.steps}/${ui.goal}", color = MaterialTheme.colorScheme.error)
                Button(onClick = { ctx.startActivity(Intent(ctx, AlarmActivity::class.java)) }) { Text("알람 화면 열기") }
            }
            AlarmSession.UiPhase.DIMMING -> Text("조명 디밍 중 · ${ui.reason}")
            AlarmSession.UiPhase.IDLE -> Unit
        }
        val next = state.next
        if (next != null) {
            Text(
                "다음 알람  ${LocalDate.parse(next.date).pretty()} ${next.time}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(next.reason, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("예정된 알람 없음")
        }
        if (settings.rule.holidayCalendarIds.isEmpty()) {
            Text("⚠ 공휴일 캘린더 미설정 — 평일 공휴일에도 울림", color = MaterialTheme.colorScheme.error)
        }
        if (state.session?.phase == Phase.DONE) Text("자동 소등 대기 중")
        TextButton(onClick = {
            scope.launch(Dispatchers.IO) { AlarmScheduler.rescheduleAll(ctx) }
        }) { Text("다시 계산") }
    }
}

@Composable
private fun AlarmTimeCard(settings: AppSettings, save: Save) {
    var picking by remember { mutableStateOf(false) }
    SectionCard("알람") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("시각", Modifier.weight(1f))
            OutlinedButton(onClick = { picking = true }) {
                Text(settings.baseTime.toString(), style = MaterialTheme.typography.titleLarge)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("해제 걸음 수", Modifier.weight(1f))
            TextButton(onClick = { save { it.copy(stepGoal = (it.stepGoal - 5).coerceAtLeast(10)) } }) { Text("−") }
            Text("${settings.stepGoal}보")
            TextButton(onClick = { save { it.copy(stepGoal = (it.stepGoal + 5).coerceAtMost(200)) } }) { Text("+") }
        }
        Text(
            "스누즈 없음 · 5분마다 볼륨 상향 · 휴무 버튼 5초 누르기로만 해제 가능",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (picking) {
        TimePickDialog("알람 시각", settings.baseTime, onDismiss = { picking = false }) { t ->
            picking = false
            save { it.copy(alarmHour = t.hour, alarmMinute = t.minute) }
        }
    }
}

@Composable
private fun OverrideCard(
    settings: AppSettings,
    request: LocalDate?,
    onRequestHandled: () -> Unit,
    save: Save,
) {
    var pickDate by remember { mutableStateOf(false) }
    var timeFor by remember { mutableStateOf<LocalDate?>(null) }

    // 23시 안내 알림에서 [시각 변경]으로 진입
    LaunchedEffect(request) {
        if (request != null) {
            timeFor = request
            onRequestHandled()
        }
    }

    SectionCard("일회성 변경") {
        Text("지정한 날짜는 휴무 판정과 관계없이 그 시각에 울림", style = MaterialTheme.typography.bodySmall)
        val entries = settings.overrides.entries.sortedBy { it.key }
        if (entries.isEmpty()) Text("없음")
        entries.forEach { (d, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${LocalDate.parse(d).pretty()}  →  $t", Modifier.weight(1f))
                TextButton(onClick = { save { it.copy(overrides = it.overrides - d) } }) { Text("삭제") }
            }
        }
        OutlinedButton(onClick = { pickDate = true }) { Text("추가") }
    }

    if (pickDate) {
        DatePickDialog(LocalDate.now().plusDays(1), onDismiss = { pickDate = false }) { d ->
            pickDate = false
            timeFor = d
        }
    }
    timeFor?.let { d ->
        val initial = settings.overrideFor(d) ?: settings.baseTime
        TimePickDialog("${d.pretty()} 알람 시각", initial, onDismiss = { timeFor = null }) { t ->
            timeFor = null
            save { it.copy(overrides = it.overrides + (d.toString() to t.toString())) }
        }
    }
}

@Composable
private fun DayOffCard(settings: AppSettings, tick: Int, save: Save) {
    val ctx = LocalContext.current
    val rule = settings.rule
    var dialog by remember { mutableStateOf<String?>(null) } // "target" | "holiday"
    var calendars by remember { mutableStateOf<List<CalendarReader.CalendarInfo>>(emptyList()) }
    LaunchedEffect(tick, dialog) {
        calendars = withContext(Dispatchers.IO) { CalendarReader.listCalendars(ctx) }
    }
    val names = calendars.associate { it.id to it.name }

    SectionCard("휴무 판정") {
        Text(
            "우선순위: 일회성 변경 > 근무 키워드 > 휴무 키워드 > 공휴일 > 주말",
            style = MaterialTheme.typography.bodySmall,
        )
        KeywordEditor("휴무 키워드 (제목에 포함되면 스킵)", rule.offKeywords) { v ->
            save { it.copy(rule = it.rule.copy(offKeywords = v)) }
        }
        KeywordEditor("근무 키워드 (주말·공휴일에도 울림)", rule.workKeywords) { v ->
            save { it.copy(rule = it.rule.copy(workKeywords = v)) }
        }
        KeywordEditor("제외 키워드 (휴무 키워드와 함께 있으면 무시)", rule.excludeKeywords) { v ->
            save { it.copy(rule = it.rule.copy(excludeKeywords = v)) }
        }
        KeywordEditor("공휴일 캘린더에서 무시할 기념일", rule.holidayExcludeKeywords) { v ->
            save { it.copy(rule = it.rule.copy(holidayExcludeKeywords = v)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("종일 일정만 키워드 판정", Modifier.weight(1f))
            Switch(checked = rule.allDayOnly, onCheckedChange = { v ->
                save { it.copy(rule = it.rule.copy(allDayOnly = v)) }
            })
        }
        HorizontalDivider()
        val targetText = if (rule.calendarIds.isEmpty()) "전체" else rule.calendarIds.joinToString { names[it] ?: "#$it" }
        Text("판정 대상 캘린더: $targetText")
        OutlinedButton(onClick = { dialog = "target" }) { Text("대상 캘린더 선택") }
        val holidayText = if (rule.holidayCalendarIds.isEmpty()) "없음" else rule.holidayCalendarIds.joinToString { names[it] ?: "#$it" }
        Text("공휴일 캘린더: $holidayText")
        OutlinedButton(onClick = { dialog = "holiday" }) { Text("공휴일 캘린더 선택") }
        if (!CalendarReader.hasPermission(ctx)) {
            Text("캘린더 권한이 필요함 (아래 권한 항목)", color = MaterialTheme.colorScheme.error)
        }
    }

    val items = calendars.map {
        SelectItem(it.id, it.name, it.account + if (it.holidayLike) " · 공휴일" else "")
    }
    when (dialog) {
        "target" -> MultiSelectDialog(
            title = "판정 대상 캘린더 (미선택 = 전체)",
            items = items,
            initial = rule.calendarIds,
            emptyText = "캘린더 없음 (권한 확인)",
            onDismiss = { dialog = null },
        ) { sel ->
            dialog = null
            save { it.copy(rule = it.rule.copy(calendarIds = sel)) }
        }
        "holiday" -> MultiSelectDialog(
            title = "공휴일 캘린더",
            items = items,
            initial = rule.holidayCalendarIds,
            emptyText = "캘린더 없음 (권한 확인)",
            onDismiss = { dialog = null },
        ) { sel ->
            dialog = null
            save { it.copy(rule = it.rule.copy(holidayCalendarIds = sel)) }
        }
    }
}

@Composable
private fun LightsCard(settings: AppSettings, tick: Int, save: Save) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = settings.smartThings
    val lights = settings.lights
    var clientId by remember(cfg.clientId) { mutableStateOf(cfg.clientId) }
    var redirect by remember(cfg.redirectUri) { mutableStateOf(cfg.redirectUri) }
    var secret by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<SmartThingsClient.Device>?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) } // "dimmer" | "switch"
    var busy by remember { mutableStateOf(false) }
    val loggedIn = remember(tick, busy) { SmartThingsAuth.isLoggedIn(ctx) }
    val hasSecret = remember(tick, busy) { SmartThingsAuth.hasClientSecret(ctx) }

    SectionCard("조명 (SmartThings)") {
        Text(
            "디밍: 알람 ${lights.dimLeadMinutes}분 전부터 ${lights.dimStepSeconds}초 간격으로 1→${lights.dimTargetLevel}% · " +
                "미해제 ${lights.switchDelayMinutes}분 후 스위치 점등 · 해제 ${lights.autoOffMinutes}분 뒤 자동 소등",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(clientId, { clientId = it }, Modifier.fillMaxWidth(), label = { Text("Client ID") }, singleLine = true)
        OutlinedTextField(
            secret, { secret = it }, Modifier.fillMaxWidth(),
            label = { Text(if (hasSecret) "Client Secret (저장됨)" else "Client Secret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        OutlinedTextField(redirect, { redirect = it }, Modifier.fillMaxWidth(), label = { Text("Redirect URI") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                save { it.copy(smartThings = it.smartThings.copy(clientId = clientId.trim(), redirectUri = redirect.trim())) }
                if (secret.isNotBlank()) SmartThingsAuth.saveClientSecret(ctx, secret)
                secret = ""
                busy = !busy
                Toast.makeText(ctx, "저장됨", Toast.LENGTH_SHORT).show()
            }) { Text("저장") }
            Button(
                enabled = cfg.clientId.isNotEmpty() && hasSecret,
                onClick = { ctx.startActivity(Intent(ctx, SmartThingsLoginActivity::class.java)) },
            ) { Text(if (loggedIn) "다시 로그인" else "로그인") }
        }
        Text(if (loggedIn) "연결됨" else "미연결", color = if (loggedIn) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)

        if (loggedIn) {
            HorizontalDivider()
            OutlinedButton(onClick = {
                scope.launch {
                    devices = SmartThingsClient.listDevices(ctx)
                    if (devices == null) Toast.makeText(ctx, "기기 목록 실패", Toast.LENGTH_SHORT).show()
                }
            }) { Text("기기 불러오기") }
            Text("디밍 조명: " + lights.dimmerIds.joinToString { lights.deviceLabels[it] ?: it }.ifEmpty { "없음" })
            Text("스위치: " + lights.switchIds.joinToString { lights.deviceLabels[it] ?: it }.ifEmpty { "없음" })
            devices?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dialog = "dimmer" }) { Text("디밍 조명 선택") }
                    OutlinedButton(onClick = { dialog = "switch" }) { Text("스위치 선택") }
                }
            }
            OutlinedButton(
                enabled = !busy && lights.dimmerIds.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
                        lightTest(ctx, lights.dimmerIds, lights.dimTargetLevel)
                        busy = false
                    }
                },
            ) { Text(if (busy) "조명 테스트 중…" else "조명 테스트 (10초 디밍 후 복구)") }
            TextButton(onClick = { SmartThingsAuth.logout(ctx); busy = !busy }) { Text("로그아웃") }
        }
    }

    val list = devices ?: emptyList()
    fun labels(ids: Set<String>) = lights.deviceLabels + list.filter { it.id in ids }.associate { it.id to it.label }
    when (dialog) {
        "dimmer" -> MultiSelectDialog(
            title = "디밍 조명 (밝기 조절 가능)",
            items = list.filter { it.hasLevel }.map { SelectItem(it.id, it.label) },
            initial = lights.dimmerIds.toSet(),
            emptyText = "밝기 조절 가능한 기기 없음",
            onDismiss = { dialog = null },
        ) { sel ->
            dialog = null
            save { it.copy(lights = it.lights.copy(dimmerIds = sel.toList(), deviceLabels = labels(sel))) }
        }
        "switch" -> MultiSelectDialog(
            title = "미해제 시 켤 스위치",
            items = list.filter { it.hasSwitch }.map { SelectItem(it.id, it.label, if (it.hasLevel) "밝기 조절 가능" else null) },
            initial = lights.switchIds.toSet(),
            emptyText = "스위치 기기 없음",
            onDismiss = { dialog = null },
        ) { sel ->
            dialog = null
            save { it.copy(lights = it.lights.copy(switchIds = sel.toList(), deviceLabels = labels(sel))) }
        }
    }
}

/** 스냅샷 → 10초 동안 1→목표 밝기 → 5초 유지 → 복구 */
private suspend fun lightTest(ctx: Context, ids: List<String>, target: Int) = withContext(Dispatchers.IO) {
    val snap = LightController.snapshot(ctx, ids)
    val targets = ids.filter { snap[it]?.on != true }
    for (i in 0..5) {
        LightController.setLevels(ctx, targets, 1 + (target - 1) * i / 5, turnOn = true)
        delay(2_000)
    }
    delay(5_000)
    LightController.restore(ctx, SessionState("test", 0, Phase.DONE, snap, dimmersTouched = true), "조명 테스트 복구")
}

@Composable
private fun PermissionCard(tick: Int) {
    val ctx = LocalContext.current
    val pkgUri = Uri.parse("package:${ctx.packageName}")
    var refresh by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    data class Item(val title: String, val granted: Boolean, val optional: Boolean = false, val onGrant: () -> Unit)

    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    fun open(action: String) = runCatching { ctx.startActivity(Intent(action, pkgUri)) }
        .onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)) }

    val items = remember(tick, refresh) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val am = ctx.getSystemService(AlarmManager::class.java)
        listOf(
            Item("알림", granted(Manifest.permission.POST_NOTIFICATIONS)) {
                launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            },
            Item("캘린더 읽기", granted(Manifest.permission.READ_CALENDAR)) {
                launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
            },
            Item("신체 활동 (폰 걸음 수)", granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
                launcher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
            },
            Item("전체 화면 알림 (잠금화면 알람)", nm.canUseFullScreenIntent()) {
                open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            },
            Item("배터리 최적화 제외", pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            },
            Item("정확한 알람", am.canScheduleExactAlarms()) {
                open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            },
            Item("다른 앱 위에 표시 (화면 켜져 있을 때 바로 알람 화면)", Settings.canDrawOverlays(ctx), optional = true) {
                open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            },
        )
    }

    SectionCard("권한") {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title)
                    Text(
                        when {
                            item.granted -> "허용됨"
                            item.optional -> "선택"
                            else -> "필요"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    )
                }
                if (!item.granted) TextButton(onClick = item.onGrant) { Text("허용") }
            }
        }
        Text(
            "방해 금지 모드에서 '알람' 허용이 꺼져 있으면 소리가 나지 않을 수 있음",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TestCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickDate by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Judgement?>(null) }

    SectionCard("테스트") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }) { Text("날짜 판정 확인") }
            OutlinedButton(onClick = {
                AlarmScheduler.scheduleTest(ctx, 10_000)
                Toast.makeText(ctx, "10초 뒤 울림 (조명 제외). 화면을 꺼두고 확인", Toast.LENGTH_LONG).show()
            }) { Text("10초 뒤 테스트 알람") }
        }
        result?.let {
            Text("${it.date.pretty()} → ${if (it.ring) "울림 ${it.time}" else "스킵"} · ${it.describe()}")
        }
    }
    if (pickDate) {
        DatePickDialog(LocalDate.now().plusDays(1), onDismiss = { pickDate = false }) { d ->
            pickDate = false
            scope.launch { result = withContext(Dispatchers.IO) { AlarmScheduler.judge(ctx, d) } }
        }
    }
}

@Composable
private fun HistoryCard(tick: Int, state: AppState) {
    val ctx = LocalContext.current
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(tick, state) {
        lines = withContext(Dispatchers.IO) { HistoryLog.read(ctx).takeLast(30).reversed() }
    }
    SectionCard("이력") {
        if (lines.isEmpty()) Text("없음")
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
