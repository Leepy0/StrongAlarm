@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leepy0.strongalarm.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leepy0.strongalarm.R
import io.github.leepy0.strongalarm.alarm.AlarmScheduler
import io.github.leepy0.strongalarm.alarm.AlarmSession
import io.github.leepy0.strongalarm.alarm.WatchLink
import io.github.leepy0.strongalarm.core.Judgement
import io.github.leepy0.strongalarm.core.KeywordMatcher
import io.github.leepy0.strongalarm.core.ReasonCode
import io.github.leepy0.strongalarm.data.AppSettings
import io.github.leepy0.strongalarm.data.CalendarReader
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Phase
import io.github.leepy0.strongalarm.data.SessionState
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.lights.LightController
import io.github.leepy0.strongalarm.lights.SmartThingsAuth
import io.github.leepy0.strongalarm.lights.SmartThingsClient
import io.github.leepy0.strongalarm.ui.components.AppIcon
import io.github.leepy0.strongalarm.ui.components.MultiSelectDialog
import io.github.leepy0.strongalarm.ui.components.SelectItem
import io.github.leepy0.strongalarm.ui.components.TimePickDialog
import io.github.leepy0.strongalarm.ui.home.DayCell
import io.github.leepy0.strongalarm.ui.home.DaySheetContent
import io.github.leepy0.strongalarm.ui.home.HomeScreen
import io.github.leepy0.strongalarm.ui.home.HomeUiState
import io.github.leepy0.strongalarm.ui.home.NextAlarm
import io.github.leepy0.strongalarm.ui.home.Readiness
import io.github.leepy0.strongalarm.ui.rules.RulesScreen
import io.github.leepy0.strongalarm.ui.rules.RulesUiState
import io.github.leepy0.strongalarm.ui.settings.HistoryScreen
import io.github.leepy0.strongalarm.ui.settings.LightsScreen
import io.github.leepy0.strongalarm.ui.settings.LightsUiState
import io.github.leepy0.strongalarm.ui.settings.PermissionKey
import io.github.leepy0.strongalarm.ui.settings.PermissionsScreen
import io.github.leepy0.strongalarm.ui.settings.SettingsScreen
import io.github.leepy0.strongalarm.ui.settings.SettingsUiState
import io.github.leepy0.strongalarm.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    ALARM("알람", R.drawable.ic_tab_alarm),
    RULES("휴무 규칙", R.drawable.ic_tab_rules),
    SETTINGS("설정", R.drawable.ic_tab_settings),
}

enum class Sub { LIGHTS, PERMISSIONS, HISTORY }

private sealed interface TimeTarget {
    data object Base : TimeTarget
    data class Day(val date: LocalDate) : TimeTarget
}

/** 판정 → 2주 스트립 칸 */
fun Judgement.toCell(s: AppSettings): DayCell {
    val label = when {
        ring -> time.hhmm()
        code == ReasonCode.WEEKEND -> "주말"
        code == ReasonCode.HOLIDAY -> "공휴일"
        code == ReasonCode.OFF_EVENT -> KeywordMatcher.firstHit(s.rule.offKeywords, detail.orEmpty()) ?: "휴무"
        else -> "쉼"
    }
    return DayCell(date, ring, time, s.overrides.containsKey(date.toString()), label, sentence())
}

/** 앱 전체: 하단 탭 + 하위 화면 + 시트·다이얼로그. 저장소와 화면을 연결하는 곳 */
@Composable
fun AppRoot(overrideRequest: LocalDate?, onOverrideHandled: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Stores.settings.flow(ctx).collectAsStateWithLifecycle()
    val appState by Stores.state.flow(ctx).collectAsStateWithLifecycle()
    val alarm by AlarmSession.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.ALARM) }
    var sub by rememberSaveable { mutableStateOf<Sub?>(null) }

    // 다른 화면(설정 앱·로그인)에서 돌아오면 다시 읽기
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }

    BackHandler(enabled = sub != null) { sub = null }
    BackHandler(enabled = sub == null && tab != Tab.ALARM) { tab = Tab.ALARM }

    val reschedule = { scope.launch(Dispatchers.IO) { AlarmScheduler.rescheduleAll(ctx) } }
    val save: ((AppSettings) -> AppSettings) -> Unit = { f ->
        Stores.settings.update(ctx, f)
        reschedule()
    }

    // ── 권한 ──
    val permissions = remember(tick) { readPermissions(ctx) }
    val missingPermissions = permissions.count { !it.granted && !it.optional }
    val calendarReadable = permissions.first { it.key == PermissionKey.CALENDAR }.granted
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        reschedule()
    }
    val grant: (PermissionKey) -> Unit = { key ->
        val runtime = runtimePermissionOf(key)
        if (runtime != null) launcher.launch(arrayOf(runtime)) else openPermissionSettings(ctx, key)
    }

    // ── 2주 판정 ──
    val today = remember(tick) { LocalDate.now() }
    var judgements by remember { mutableStateOf<List<Judgement>>(emptyList()) }
    LaunchedEffect(settings, appState.next, today) {
        judgements = withContext(Dispatchers.IO) {
            val judge = AlarmScheduler.judgeFunction(ctx, today, 15)
            (0L until 14L).map { judge(today.plusDays(it)) }
        }
    }
    val days = judgements.map { it.toCell(settings) }

    // ── 워치·캘린더·조명 ──
    var watchNodes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(tick) { watchNodes = WatchLink.nodes(ctx).size }

    var calendars by remember { mutableStateOf<List<CalendarReader.CalendarInfo>>(emptyList()) }
    LaunchedEffect(tick) { calendars = withContext(Dispatchers.IO) { CalendarReader.listCalendars(ctx) } }

    // 공휴일 캘린더 자동 선택 (캘린더 권한을 처음 받았을 때 1회)
    LaunchedEffect(calendars) {
        val s = Stores.settings.get(ctx)
        if (!s.holidayAutoDetected && calendars.isNotEmpty()) {
            val found = calendars.filter { it.holidayLike }.map { it.id }.toSet()
            save {
                it.copy(
                    holidayAutoDetected = true,
                    rule = if (it.rule.holidayCalendarIds.isEmpty()) it.rule.copy(holidayCalendarIds = found) else it.rule,
                )
            }
        }
    }

    val loggedIn = remember(tick, settings.smartThings) { SmartThingsAuth.isLoggedIn(ctx) }
    val hasSecret = remember(tick, settings.smartThings) { SmartThingsAuth.hasClientSecret(ctx) }
    val lights = settings.lights
    val linkedCount = (lights.dimmerIds + lights.switchIds).distinct().size
    var devices by remember { mutableStateOf<List<SmartThingsClient.Device>?>(null) }
    var lightBusy by remember { mutableStateOf(false) }

    // ── 시트·다이얼로그 상태 ──
    var sheetDate by remember { mutableStateOf<LocalDate?>(null) }
    var sheetCell by remember { mutableStateOf<DayCell?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var calendarDialog by remember { mutableStateOf<String?>(null) } // holiday | target
    var deviceDialog by remember { mutableStateOf<String?>(null) }   // dimmer | switch

    LaunchedEffect(overrideRequest) {
        if (overrideRequest != null) {
            timeTarget = TimeTarget.Day(overrideRequest)
            onOverrideHandled()
        }
    }
    LaunchedEffect(sheetDate, judgements) {
        val d = sheetDate
        sheetCell = when {
            d == null -> null
            else -> days.firstOrNull { it.date == d }
                ?: withContext(Dispatchers.IO) { AlarmScheduler.judge(ctx, d) }.toCell(Stores.settings.get(ctx))
        }
    }

    fun setOverride(date: LocalDate, time: LocalTime) =
        save { it.copy(overrides = it.overrides + (date.toString() to time.toString())) }

    fun clearOverride(date: LocalDate) = save { it.copy(overrides = it.overrides - date.toString()) }

    fun pickDevices(kind: String) {
        scope.launch {
            if (devices == null) devices = SmartThingsClient.listDevices(ctx)
            if (devices == null) {
                Toast.makeText(ctx, "기기 목록을 불러오지 못했어요. 연결을 확인해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                deviceDialog = kind
            }
        }
    }

    Scaffold(
        containerColor = Palette.Night,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (sub == null) BottomBar(tab) { tab = it } },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            // 폴드 펼친 화면에서 너무 넓어지지 않게
            Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                when (sub) {
                    Sub.LIGHTS -> LightsScreen(
                        state = LightsUiState(
                            clientId = settings.smartThings.clientId,
                            redirectUri = settings.smartThings.redirectUri,
                            hasSecret = hasSecret,
                            loggedIn = loggedIn,
                            dimmers = lights.dimmerIds.map { lights.deviceLabels[it] ?: it },
                            switches = lights.switchIds.map { lights.deviceLabels[it] ?: it },
                            leadMinutes = lights.dimLeadMinutes,
                            switchDelayMinutes = lights.switchDelayMinutes,
                            autoOffMinutes = lights.autoOffMinutes,
                            busy = lightBusy,
                        ),
                        onBack = { sub = null },
                        onSaveCredentials = { id, secret, redirect ->
                            save { it.copy(smartThings = it.smartThings.copy(clientId = id, redirectUri = redirect)) }
                            if (secret.isNotBlank()) SmartThingsAuth.saveClientSecret(ctx, secret)
                        },
                        onLogin = { ctx.startActivity(Intent(ctx, SmartThingsLoginActivity::class.java)) },
                        onLogout = {
                            SmartThingsAuth.logout(ctx)
                            devices = null
                            tick++
                        },
                        onPickDimmers = { pickDevices("dimmer") },
                        onPickSwitches = { pickDevices("switch") },
                        onTest = {
                            lightBusy = true
                            scope.launch {
                                lightTest(ctx, lights.dimmerIds, lights.dimTargetLevel)
                                lightBusy = false
                            }
                        },
                    )

                    Sub.PERMISSIONS -> PermissionsScreen(permissions, onBack = { sub = null }, onGrant = grant)

                    Sub.HISTORY -> {
                        var lines by remember { mutableStateOf<List<String>>(emptyList()) }
                        LaunchedEffect(appState, tick) {
                            lines = withContext(Dispatchers.IO) { HistoryLog.read(ctx).takeLast(120) }
                        }
                        HistoryScreen(lines, onBack = { sub = null })
                    }

                    null -> when (tab) {
                        Tab.ALARM -> {
                            val nextPlan = appState.next
                            val next = nextPlan?.let { p ->
                                val date = LocalDate.parse(p.date)
                                NextAlarm(
                                    date = date,
                                    time = LocalTime.parse(p.time),
                                    reason = judgements.firstOrNull { it.date == date }?.sentence() ?: p.reason,
                                )
                            }
                            HomeScreen(
                                state = HomeUiState(
                                    today = today,
                                    next = next,
                                    baseTime = settings.baseTime,
                                    days = days,
                                    ringing = alarm.phase == AlarmSession.UiPhase.RINGING || alarm.phase == AlarmSession.UiPhase.PAUSED,
                                    dimming = alarm.phase == AlarmSession.UiPhase.DIMMING,
                                    readiness = Readiness(
                                        missingPermissions = missingPermissions,
                                        watchNodes = watchNodes,
                                        lightsLinked = if (loggedIn) linkedCount else null,
                                        holidayCalendarSet = settings.rule.holidayCalendarIds.isNotEmpty(),
                                        calendarReadable = calendarReadable,
                                    ),
                                ),
                                onDayClick = { sheetDate = it },
                                onEditBaseTime = { timeTarget = TimeTarget.Base },
                                onOpenAlarm = { ctx.startActivity(Intent(ctx, AlarmActivity::class.java)) },
                                onOpenPermissions = { sub = Sub.PERMISSIONS },
                                onOpenRules = { tab = Tab.RULES },
                            )
                        }

                        Tab.RULES -> RulesScreen(
                            state = RulesUiState(
                                rule = settings.rule,
                                calendarNames = calendars.associate { it.id to it.name },
                                overrideCount = settings.overrides.size,
                                calendarReadable = calendarReadable,
                            ),
                            onChange = { r -> save { it.copy(rule = r) } },
                            onPickHolidayCalendars = { calendarDialog = "holiday" },
                            onPickTargetCalendars = { calendarDialog = "target" },
                            onGrantCalendar = { grant(PermissionKey.CALENDAR) },
                        )

                        Tab.SETTINGS -> SettingsScreen(
                            state = SettingsUiState(
                                stepGoal = settings.stepGoal,
                                lightsSummary = when {
                                    !loggedIn -> null
                                    linkedCount == 0 -> "기기 선택 필요"
                                    else -> "${linkedCount}개"
                                },
                                missingPermissions = missingPermissions,
                            ),
                            onStepGoal = { v -> save { it.copy(stepGoal = v) } },
                            onOpenLights = { sub = Sub.LIGHTS },
                            onOpenPermissions = { sub = Sub.PERMISSIONS },
                            onTestAlarm = {
                                AlarmScheduler.scheduleTest(ctx, 10_000)
                                Toast.makeText(ctx, "10초 뒤 울려요. 화면을 꺼두고 기다려보세요.", Toast.LENGTH_LONG).show()
                            },
                            onOpenHistory = { sub = Sub.HISTORY },
                        )
                    }
                }
            }
        }
    }

    // ── 날짜 시트 ──
    if (sheetDate != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetDate = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Palette.Dusk,
        ) {
            sheetCell?.let { cell ->
                DaySheetContent(
                    day = cell,
                    baseTime = settings.baseTime,
                    onRingAnyway = {
                        setOverride(cell.date, settings.baseTime)
                        sheetDate = null
                    },
                    onPickTime = {
                        timeTarget = TimeTarget.Day(cell.date)
                        sheetDate = null
                    },
                    onClearOverride = {
                        clearOverride(cell.date)
                        sheetDate = null
                    },
                )
            }
        }
    }

    // ── 시각 선택 ──
    when (val t = timeTarget) {
        TimeTarget.Base -> TimePickDialog("매일 울릴 시각", settings.baseTime, onDismiss = { timeTarget = null }) { time ->
            timeTarget = null
            save { it.copy(alarmHour = time.hour, alarmMinute = time.minute) }
        }
        is TimeTarget.Day -> TimePickDialog(
            "${t.date.longKo()}만",
            settings.overrideFor(t.date) ?: settings.baseTime,
            onDismiss = { timeTarget = null },
        ) { time ->
            timeTarget = null
            setOverride(t.date, time)
        }
        null -> Unit
    }

    // ── 캘린더 선택 ──
    val calendarItems = calendars.map {
        SelectItem(it.id, it.name, if (it.holidayLike) "${it.account} (공휴일로 보임)" else it.account)
    }
    when (calendarDialog) {
        "holiday" -> MultiSelectDialog(
            title = "공휴일 캘린더",
            items = calendarItems,
            initial = settings.rule.holidayCalendarIds,
            emptyText = "캘린더가 없어요. 캘린더 권한과 Google 캘린더의 '대한민국의 휴일' 동기화를 확인해주세요.",
            onDismiss = { calendarDialog = null },
        ) { sel ->
            calendarDialog = null
            save { it.copy(rule = it.rule.copy(holidayCalendarIds = sel)) }
        }
        "target" -> MultiSelectDialog(
            title = "일정을 찾을 캘린더",
            items = calendarItems.filter { it.key !in settings.rule.holidayCalendarIds },
            initial = settings.rule.calendarIds,
            emptyText = "캘린더가 없어요. 캘린더 권한을 확인해주세요.",
            onDismiss = { calendarDialog = null },
        ) { sel ->
            calendarDialog = null
            save { it.copy(rule = it.rule.copy(calendarIds = sel)) }
        }
    }

    // ── 조명 기기 선택 ──
    val list = devices.orEmpty()
    fun labels(ids: Set<String>) = lights.deviceLabels + list.filter { it.id in ids }.associate { it.id to it.label }
    when (deviceDialog) {
        "dimmer" -> MultiSelectDialog(
            title = "디밍 조명",
            items = list.filter { it.hasLevel }.map { SelectItem(it.id, it.label) },
            initial = lights.dimmerIds.toSet(),
            emptyText = "밝기 조절되는 기기가 없어요",
            onDismiss = { deviceDialog = null },
        ) { sel ->
            deviceDialog = null
            save { it.copy(lights = it.lights.copy(dimmerIds = sel.toList(), deviceLabels = labels(sel))) }
        }
        "switch" -> MultiSelectDialog(
            title = "안 끄면 켤 스위치",
            items = list.filter { it.hasSwitch }.map { SelectItem(it.id, it.label, if (it.hasLevel) "밝기 조절 가능" else null) },
            initial = lights.switchIds.toSet(),
            emptyText = "스위치 기기가 없어요",
            onDismiss = { deviceDialog = null },
        ) { sel ->
            deviceDialog = null
            save { it.copy(lights = it.lights.copy(switchIds = sel.toList(), deviceLabels = labels(sel))) }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = Palette.Line)
        NavigationBar(containerColor = Palette.Night, tonalElevation = 0.dp) {
            Tab.entries.forEach { t ->
                val selected = t == current
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(t) },
                    icon = { AppIcon(t.icon, if (selected) Palette.Sun else Palette.Mist) },
                    label = { Text(t.label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Palette.SunSoft,
                        selectedTextColor = Palette.Sun,
                        unselectedTextColor = Palette.Mist,
                    ),
                )
            }
        }
    }
}

/** 스냅샷 → 10초 동안 1→목표 밝기 → 5초 유지 → 원래대로 */
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
