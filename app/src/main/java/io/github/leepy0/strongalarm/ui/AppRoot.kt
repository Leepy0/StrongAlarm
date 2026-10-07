@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leepy0.strongalarm.ui

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leepy0.strongalarm.BuildConfig
import io.github.leepy0.strongalarm.R
import io.github.leepy0.strongalarm.alarm.AlarmScheduler
import io.github.leepy0.strongalarm.alarm.AlarmSound
import io.github.leepy0.strongalarm.alarm.AlarmSession
import io.github.leepy0.strongalarm.alarm.WatchLink
import io.github.leepy0.strongalarm.core.DayOffRule
import io.github.leepy0.strongalarm.core.Judgement
import io.github.leepy0.strongalarm.core.KeywordMatcher
import io.github.leepy0.strongalarm.core.ReasonCode
import io.github.leepy0.strongalarm.data.AppSettings
import io.github.leepy0.strongalarm.data.CalendarReader
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Phase
import io.github.leepy0.strongalarm.data.SessionState
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.data.dayLocked
import io.github.leepy0.strongalarm.lights.LightController
import io.github.leepy0.strongalarm.lights.SmartThingsAuth
import io.github.leepy0.strongalarm.lights.SmartThingsClient
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
import io.github.leepy0.strongalarm.ui.settings.AppVersionUi
import io.github.leepy0.strongalarm.ui.settings.DownloadUi
import io.github.leepy0.strongalarm.ui.settings.HistoryScreen
import io.github.leepy0.strongalarm.ui.settings.LightTestState
import io.github.leepy0.strongalarm.ui.settings.LightsScreen
import io.github.leepy0.strongalarm.ui.settings.LightsUiState
import io.github.leepy0.strongalarm.ui.settings.PermissionKey
import io.github.leepy0.strongalarm.ui.settings.PermissionsScreen
import io.github.leepy0.strongalarm.ui.settings.SettingsScreen
import io.github.leepy0.strongalarm.ui.settings.SettingsUiState
import io.github.leepy0.strongalarm.ui.theme.Palette
import io.github.leepy0.strongalarm.update.ApkDownloads
import io.github.leepy0.strongalarm.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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

/** 판정 → 2주 격자 칸 */
fun Judgement.toCell(s: AppSettings): DayCell {
    val label = when {
        ring -> time.hhmm()
        code == ReasonCode.WEEKEND -> "주말"
        code == ReasonCode.HOLIDAY -> "공휴일"
        code == ReasonCode.OFF_EVENT -> KeywordMatcher.firstHit(s.rule.offKeywords, detail.orEmpty()) ?: "휴무"
        else -> "쉼"
    }
    return DayCell(
        date, ring, time, s.overrides.containsKey(date.toString()), label, sentence(),
        confirmed = ring && s.isConfirmed(date),
        manualOff = code == ReasonCode.MANUAL_OFF,
    )
}

private fun DayOffRule.keywordCount() =
    offKeywords.size + workKeywords.size + excludeKeywords.size + holidayExcludeKeywords.size

/**
 * 앱 전체 틀. 좁은 화면은 하단 탭, 폴드 펼친 화면(600dp 이상)은 왼쪽 레일.
 * 저장소와 화면을 연결하고, 되돌릴 수 있는 삭제는 스낵바 '되돌리기'로 처리
 */
@Composable
fun AppRoot(
    overrideRequest: LocalDate?,
    onOverrideHandled: () -> Unit,
    openUpdate: Boolean = false,
    onOpenUpdateHandled: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
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

    /** 되돌릴 수 있는 동작: 확인 없이 실행하고 스낵바로 되돌리기 제공 */
    fun undoable(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "되돌리기", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) undo()
        }
    }

    // ── 권한 ──
    val permissions = remember(tick) { readPermissions(ctx) }
    val missingPermissions = permissions.count { !it.granted && !it.optional }
    val calendarReadable = permissions.first { it.key == PermissionKey.CALENDAR }.granted
    // 설정 앱에서 권한을 바꾸고 돌아오면 판정·재등록 다시 (캘린더 권한 등)
    var lastGranted by remember { mutableStateOf<Set<PermissionKey>?>(null) }
    LaunchedEffect(permissions) {
        val granted = permissions.filter { it.granted }.map { it.key }.toSet()
        if (lastGranted != null && lastGranted != granted) reschedule()
        lastGranted = granted
    }
    var requestedKey by remember { mutableStateOf<PermissionKey?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        tick++
        reschedule()
        // '다시 묻지 않음' 상태면 시스템 대화상자가 안 뜨므로 앱 설정 화면으로
        val key = requestedKey
        val permission = key?.let(::runtimePermissionOf)
        val activity = ctx as? Activity
        if (key != null && permission != null && result[permission] == false &&
            activity?.shouldShowRequestPermissionRationale(permission) == false
        ) {
            openPermissionSettings(ctx, key)
        }
        requestedKey = null
    }
    val grant: (PermissionKey) -> Unit = { key ->
        val runtime = runtimePermissionOf(key)
        if (runtime != null) {
            requestedKey = key
            launcher.launch(arrayOf(runtime))
        } else {
            openPermissionSettings(ctx, key)
        }
    }

    // ── 새 버전: 화면에 돌아올 때 10분 이상 지났으면 확인 ──
    val updateState by Updater.state.collectAsStateWithLifecycle()
    LaunchedEffect(tick) {
        val last = Stores.state.get(ctx).updateCheckedAt ?: 0L
        if (Updater.state.value == Updater.State.Idle || System.currentTimeMillis() - last > 10 * 60_000L) {
            Updater.check(ctx)
        }
    }
    LaunchedEffect(openUpdate) {
        if (openUpdate) {
            sub = null
            tab = Tab.SETTINGS
            onOpenUpdateHandled()
        }
    }
    val newRemote = (updateState as? Updater.State.Available)?.remote
    // 다운로드 진행률: 받는 중이면 0.5초마다 갱신
    val apkDownload by ApkDownloads.progress.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(apkDownload?.id) {
        // 화면이 보일 때만, 조회는 IO 스레드에서
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (withContext(Dispatchers.IO) { ApkDownloads.poll(ctx) }) delay(500)
        }
    }

    // ── 현재 시각: 남은 시간 표시용, 분이 바뀔 때마다 갱신 ──
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tick) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(60_000 - nowMillis % 60_000 + 50)
        }
    }

    // ── 이번 주·다음 주 판정 (오늘 ~ 다음 주 일요일) ──
    val today = remember(tick, nowMillis) { LocalDate.now() }
    var judgements by remember { mutableStateOf<List<Judgement>>(emptyList()) }
    // 판정에 영향 있는 설정만 키로 (볼륨·걸음 수 바꿀 때 캘린더 재조회하지 않게)
    LaunchedEffect(
        settings.rule, settings.overrides, settings.skips, settings.alarmHour, settings.alarmMinute,
        appState.next, today,
    ) {
        val count = 14L - (today.dayOfWeek.value - 1)
        judgements = withContext(Dispatchers.IO) {
            val judge = AlarmScheduler.judgeFunction(ctx, today, count.toInt() + 1)
            (0L until count).map { judge(today.plusDays(it)) }
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
    var loadingDevices by remember { mutableStateOf(false) }
    var lightTestState by remember { mutableStateOf<LightTestState?>(null) }
    var lightTestJob by remember { mutableStateOf<Job?>(null) }
    var previewSound by remember { mutableStateOf<AlarmSound?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }

    // ── 시트·다이얼로그 상태 ──
    var sheetDate by remember { mutableStateOf<LocalDate?>(null) }
    var sheetCell by remember { mutableStateOf<DayCell?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var calendarDialog by remember { mutableStateOf<String?>(null) } // holiday | target
    var deviceDialog by remember { mutableStateOf<String?>(null) }   // dimmer | switch
    var confirmLogout by remember { mutableStateOf(false) }

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

    /** 되돌릴 것 없는 결과 알림 */
    fun notify(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                message,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }

    /**
     * 날짜별 지정 변경 + 스낵바 되돌리기 (그 날짜 상태만 복구).
     * @param guard true면 알람 진행 중(디밍·울림)인 날짜는 막음 — 전날 결정을 아침에 뒤집지 못하게
     */
    fun changeDay(date: LocalDate, message: String, guard: Boolean = false, f: (AppSettings) -> AppSettings) {
        if (guard && Stores.state.get(ctx).dayLocked(date)) {
            notify("알람이 진행 중이라 바꿀 수 없어요. 걸어서 꺼주세요.")
            return
        }
        val before = settings
        save(f)
        undoable(message) { save { it.restoreDay(date, before) } }
    }

    fun setOverride(date: LocalDate, time: LocalTime) =
        changeDay(date, "${date.pretty()} ${time.hhmm()}에 울려요") { it.withTime(date, time) }

    fun skipDay(date: LocalDate) =
        changeDay(date, "${date.pretty()}은 쉬는 날로 정했어요", guard = true) { it.withSkip(date) }

    fun clearSkip(date: LocalDate) =
        changeDay(date, "${date.pretty()} 쉬는 날 지정을 취소했어요") { it.withoutSkip(date) }

    fun confirmDay(date: LocalDate) =
        changeDay(date, "${date.pretty()} 울림 확인 · 걸어야만 꺼져요") { it.withConfirm(date) }

    fun unconfirmDay(date: LocalDate) =
        changeDay(date, "${date.pretty()} 울림 확인을 취소했어요", guard = true) { it.withoutConfirm(date) }

    fun setBaseTime(time: LocalTime) {
        val old = settings.baseTime
        if (old == time) return
        save { it.copy(alarmHour = time.hour, alarmMinute = time.minute) }
        undoable("이제 매일 ${time.hhmm()}에 울려요") { save { it.copy(alarmHour = old.hour, alarmMinute = old.minute) } }
    }

    /** 테스트 알람: 정확한 알람 권한이 없으면 울리지 않으므로 먼저 확인 */
    fun testAlarm() {
        if (alarm.phase != AlarmSession.UiPhase.IDLE) {
            notify(if (alarm.phase == AlarmSession.UiPhase.DIMMING) "조명 디밍 중엔 테스트할 수 없어요" else "알람이 울리는 중이에요")
            return
        }
        if (!ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            notify("정확한 알람 권한이 없어 테스트할 수 없어요", "권한 설정") { sub = Sub.PERMISSIONS }
            return
        }
        AlarmScheduler.scheduleTest(ctx, 10_000)
        notify("10초 뒤 울려요. 화면을 꺼두고 기다려보세요.")
    }

    fun openUrl(url: String) {
        runCatching { ctx.startActivity(Updater.browserIntent(url)) }.onFailure { notify("브라우저를 열 수 없어요") }
    }

    /** 시스템 다운로드로 APK 받기 → 다운로드 완료 알림·목록에서 시스템 설치 */
    fun download(kind: ApkDownloads.Kind) {
        val r = newRemote ?: return
        val phone = kind == ApkDownloads.Kind.PHONE
        val version = if (phone) r.versionName else "0.2.${r.watchVersionCode}"
        runCatching { ApkDownloads.start(ctx, kind, version, if (phone) r.phoneSha256 else r.watchSha256) }
            .onFailure { notify("다운로드를 시작하지 못했어요. 웹에서 받아주세요.", "웹에서 받기") { openUrl(Updater.RELEASE_PAGE) } }
    }

    /** 알람 소리 미리 듣기 (4초). 다시 누르면 멈춤. 끝나면 원래 볼륨 복구 */
    fun togglePreview() {
        previewJob?.let {
            it.cancel()
            return
        }
        if (alarm.phase == AlarmSession.UiPhase.RINGING || alarm.phase == AlarmSession.UiPhase.PAUSED) {
            notify("알람이 울리는 중이에요")
            return
        }
        previewJob = scope.launch {
            val s = AlarmSound(ctx)
            previewSound = s
            try {
                s.setVolume(Stores.settings.get(ctx).alarmVolume / 100f)
                s.start()
                delay(4_000)
            } finally {
                s.stop()
                previewSound = null
                previewJob = null
            }
        }
    }

    /** 조명 테스트: 진행률 표시, 취소해도 원래 밝기로 복구 */
    fun startLightTest() {
        if (lightTestJob != null) return
        lightTestJob = scope.launch {
            try {
                val ok = lightTest(ctx, lights.dimmerIds, lights.dimTargetLevel) { lightTestState = it }
                if (!ok) notify("조명 상태를 못 읽었어요. SmartThings 로그인과 인터넷을 확인해주세요.")
            } finally {
                lightTestState = null
                lightTestJob = null
            }
        }
    }

    fun clearOverride(date: LocalDate) =
        changeDay(date, "${date.pretty()} 바꾼 시각을 취소했어요") { it.copy(overrides = it.overrides - date.toString()) }

    fun changeRule(r: DayOffRule) {
        val old = settings.rule
        save { it.copy(rule = r) }
        if (r.keywordCount() < old.keywordCount()) {
            undoable("키워드를 지웠어요") { save { it.copy(rule = old) } }
        }
    }

    fun pickDevices(kind: String) {
        scope.launch {
            if (devices == null) {
                loadingDevices = true
                devices = SmartThingsClient.listDevices(ctx)
                loadingDevices = false
            }
            if (devices != null) {
                deviceDialog = kind
                return@launch
            }
            // 실패: 무엇·왜·해결법 + 다시 시도
            val r = snackbar.showSnackbar(
                "기기 목록을 못 불러왔어요. 인터넷과 SmartThings 연결을 확인해주세요.",
                actionLabel = "다시 시도",
                duration = SnackbarDuration.Long,
            )
            if (r == SnackbarResult.ActionPerformed) pickDevices(kind)
        }
    }

    // ── 내비게이션: 폭 600dp 이상이면 레일, 하위 화면에선 숨김 ──
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val layoutType = when {
        sub != null -> NavigationSuiteType.None
        wide -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Palette.SunText,
            selectedTextColor = Palette.SunText,
            indicatorColor = Palette.SunSoft,
            unselectedIconColor = Palette.Mist,
            unselectedTextColor = Palette.Mist,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = Palette.SunText,
            selectedTextColor = Palette.SunText,
            indicatorColor = Palette.SunSoft,
            unselectedIconColor = Palette.Mist,
            unselectedTextColor = Palette.Mist,
        ),
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { t ->
                item(
                    selected = t == tab,
                    onClick = { tab = t },
                    icon = { Icon(painterResource(t.icon), contentDescription = null) },
                    label = { Text(t.label) },
                    colors = itemColors,
                )
            }
        },
        layoutType = layoutType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = Palette.Dusk,
            navigationRailContainerColor = Palette.Dusk,
        ),
        containerColor = Palette.Night,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // 레일일 때는 하단 바가 없으니 제스처 바 영역을 직접 비움
                .then(if (layoutType == NavigationSuiteType.NavigationRail) Modifier.navigationBarsPadding() else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 폴드 펼친 화면에서 줄 길이가 너무 길어지지 않게
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
                            test = lightTestState,
                            loadingDevices = loadingDevices,
                        ),
                        onBack = { sub = null },
                        onSaveCredentials = { id, secret, redirect ->
                            save { it.copy(smartThings = it.smartThings.copy(clientId = id, redirectUri = redirect)) }
                            if (secret.isNotBlank()) SmartThingsAuth.saveClientSecret(ctx, secret)
                        },
                        onLogin = { ctx.startActivity(Intent(ctx, SmartThingsLoginActivity::class.java)) },
                        onLogout = { confirmLogout = true },
                        onPickDimmers = { pickDevices("dimmer") },
                        onPickSwitches = { pickDevices("switch") },
                        onTest = ::startLightTest,
                        onCancelTest = { lightTestJob?.cancel() },
                    )

                    Sub.PERMISSIONS -> PermissionsScreen(permissions, onBack = { sub = null }, onGrant = grant)

                    Sub.HISTORY -> {
                        var lines by remember { mutableStateOf<List<String>>(emptyList()) }
                        LaunchedEffect(appState, tick) {
                            lines = withContext(Dispatchers.IO) { HistoryLog.read(ctx).takeLast(120) }
                        }
                        HistoryScreen(lines, onBack = { sub = null }, onTestAlarm = ::testAlarm)
                    }

                    null -> when (tab) {
                        Tab.ALARM -> {
                            val next = appState.next?.let { p ->
                                val date = LocalDate.parse(p.date)
                                NextAlarm(
                                    date = date,
                                    time = LocalTime.parse(p.time),
                                    reason = judgements.firstOrNull { it.date == date }?.sentence() ?: p.reason,
                                    ringAt = p.ringAt,
                                    confirmed = settings.isConfirmed(date),
                                    locked = appState.dayLocked(date),
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
                                        lastCheck = appState.lastCheck,
                                        lastNightly = appState.lastNightlyAt,
                                        lastCheckCalendarOk = appState.lastCheckCalendarOk,
                                        lastResult = appState.lastResult,
                                        lastResultAt = appState.lastResultAt,
                                        lightsAuthError = appState.lightsAuthError,
                                        newVersion = newRemote?.versionName,
                                    ),
                                    nowMillis = nowMillis,
                                ),
                                onDayClick = { sheetDate = it },
                                onEditBaseTime = { timeTarget = TimeTarget.Base },
                                onOpenAlarm = { ctx.startActivity(Intent(ctx, AlarmActivity::class.java)) },
                                onOpenPermissions = { sub = Sub.PERMISSIONS },
                                onOpenRules = { tab = Tab.RULES },
                                onOpenLights = { sub = Sub.LIGHTS },
                                onOpenUpdate = { tab = Tab.SETTINGS },
                                onToggleConfirm = {
                                    next?.let { n -> if (n.confirmed) unconfirmDay(n.date) else confirmDay(n.date) }
                                },
                            )
                        }

                        Tab.RULES -> RulesScreen(
                            state = RulesUiState(
                                rule = settings.rule,
                                calendarNames = calendars.associate { it.id to it.name },
                                overrideCount = settings.overrides.size + settings.skips.size,
                                calendarReadable = calendarReadable,
                            ),
                            onChange = ::changeRule,
                            onPickHolidayCalendars = { calendarDialog = "holiday" },
                            onPickTargetCalendars = { calendarDialog = "target" },
                            onGrantCalendar = { grant(PermissionKey.CALENDAR) },
                        )

                        Tab.SETTINGS -> SettingsScreen(
                            state = SettingsUiState(
                                stepGoal = settings.stepGoal,
                                alarmVolume = settings.alarmVolume,
                                previewing = previewSound != null,
                                app = AppVersionUi(
                                    installed = remember { Updater.installedVersionName(ctx) },
                                    newVersion = newRemote?.versionName,
                                    notes = newRemote?.let { r -> Updater.notesSince(ctx, r).map { it.s } }.orEmpty(),
                                    status = when (val u = updateState) {
                                        is Updater.State.Failed -> u.message
                                        else -> appState.updateCheckedAt
                                            ?.let { "최신이에요 · ${agoKo(it, nowMillis)} 확인" }
                                            ?: "아직 확인 안 함"
                                    },
                                    checking = updateState == Updater.State.Checking,
                                    watchChanged = (newRemote?.watchVersionCode ?: 0) > BuildConfig.WATCH_VERSION_CODE,
                                    download = apkDownload?.let { d ->
                                        DownloadUi(d.kind.label, d.fraction, d.done, d.reason.takeIf { d.failed })
                                    },
                                ),
                                lightsSummary = when {
                                    !loggedIn -> null
                                    appState.lightsAuthError -> "다시 로그인 필요"
                                    linkedCount == 0 -> "기기 선택 필요"
                                    else -> "${linkedCount}개"
                                },
                                missingPermissions = missingPermissions,
                            ),
                            // 판정과 무관 → 재등록 없이 저장만
                            onStepGoal = { v -> Stores.settings.update(ctx) { it.copy(stepGoal = v) } },
                            onVolume = { v ->
                                // 판정과 무관하므로 재등록 없이 저장, 미리 듣는 중이면 즉시 반영
                                Stores.settings.update(ctx) { it.copy(alarmVolume = v) }
                                previewSound?.setVolume(v / 100f)
                            },
                            onPreview = ::togglePreview,
                            onOpenLights = { sub = Sub.LIGHTS },
                            onOpenPermissions = { sub = Sub.PERMISSIONS },
                            onTestAlarm = ::testAlarm,
                            onOpenHistory = { sub = Sub.HISTORY },
                            onCheckUpdate = { scope.launch { Updater.check(ctx, silent = false) } },
                            onDownloadPhone = { download(ApkDownloads.Kind.PHONE) },
                            onDownloadWatch = { download(ApkDownloads.Kind.WATCH) },
                            onOpenDownloads = {
                                runCatching { ctx.startActivity(ApkDownloads.openDownloadsIntent()) }
                                    .onFailure { notify("내 파일 > 다운로드에서 StrongAlarm APK를 눌러 설치해주세요") }
                            },
                            onOpenReleasePage = { openUrl(Updater.RELEASE_PAGE) },
                        )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }

    // ── 날짜 시트 ──
    if (sheetDate != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetDate = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Palette.Dusk,
        ) {
            val cell = sheetCell?.takeIf { it.date == sheetDate }
            if (cell == null) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Palette.Mist, strokeWidth = 2.dp)
                }
            } else {
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
                    confirmable = appState.next?.date == cell.date.toString(),
                    locked = appState.dayLocked(cell.date),
                    onSkip = {
                        skipDay(cell.date)
                        sheetDate = null
                    },
                    onClearSkip = {
                        clearSkip(cell.date)
                        sheetDate = null
                    },
                    onConfirm = {
                        confirmDay(cell.date)
                        sheetDate = null
                    },
                    onUnconfirm = {
                        unconfirmDay(cell.date)
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
            setBaseTime(time)
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

    // ── 연결 끊기 확인 (되돌릴 수 없음: 다시 로그인 필요) ──
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = Palette.Dusk,
            title = { Text("SmartThings 연결을 끊을까요?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "알람 날 조명 제어가 멈춰요. 다시 쓰려면 로그인을 다시 해야 해요. 고른 조명 목록은 남아요.",
                    color = Palette.Mist,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    SmartThingsAuth.logout(ctx)
                    devices = null
                    tick++
                    scope.launch(Dispatchers.IO) { HistoryLog.add(ctx, "SmartThings 연결 끊음") }
                }) { Text("연결 끊기", color = Palette.Ember) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("취소", color = Palette.Ink) } },
        )
    }
}

private const val TEST_SEGMENT_MS = 1_600L

/**
 * 스냅샷 → 1.6초 간격 5단계로 1→목표 밝기 → 1.6초 유지 → 원래대로 (약 10초).
 * 취소돼도 finally에서 원래 상태로 복구
 */
private suspend fun lightTest(ctx: Context, ids: List<String>, target: Int, onState: (LightTestState) -> Unit): Boolean {
    onState(LightTestState(0f))
    val snap = withContext(Dispatchers.IO) { LightController.snapshot(ctx, ids) }
    // 상태를 하나도 못 읽으면(로그인 만료·네트워크) 진행하지 않음
    if (snap.values.all { it.on == null }) return false
    try {
        // 꺼져 있던 게 확인된 조명만
        val targets = ids.filter { snap[it]?.on == false }
        val segments = 6
        for (i in 0 until segments) {
            // 각 구간 시작 시 구간 끝 값을 넘기면 화면에서 1.6초 동안 부드럽게 채움
            onState(LightTestState((i + 1f) / segments))
            if (i < 5) {
                withContext(Dispatchers.IO) { LightController.setLevels(ctx, targets, 1 + (target - 1) * i / 4, turnOn = true) }
            }
            delay(TEST_SEGMENT_MS)
        }
    } finally {
        onState(LightTestState(1f, restoring = true))
        withContext(NonCancellable + Dispatchers.IO) {
            LightController.restore(ctx, SessionState("test", 0, Phase.DONE, snap, dimmersTouched = true), "조명 테스트 복구")
        }
    }
    return true
}
