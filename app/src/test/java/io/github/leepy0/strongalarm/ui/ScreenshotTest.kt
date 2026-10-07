package io.github.leepy0.strongalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.leepy0.strongalarm.core.DayOffRule
import io.github.leepy0.strongalarm.ui.alarm.AlarmScreen
import io.github.leepy0.strongalarm.ui.alarm.AlarmUiState
import io.github.leepy0.strongalarm.ui.home.DayCell
import io.github.leepy0.strongalarm.ui.home.DaySheetContent
import io.github.leepy0.strongalarm.ui.home.HomeScreen
import io.github.leepy0.strongalarm.ui.home.HomeUiState
import io.github.leepy0.strongalarm.ui.home.NextAlarm
import io.github.leepy0.strongalarm.ui.home.Readiness
import io.github.leepy0.strongalarm.ui.rules.RulesScreen
import io.github.leepy0.strongalarm.ui.rules.RulesUiState
import io.github.leepy0.strongalarm.ui.settings.AppVersionUi
import io.github.leepy0.strongalarm.ui.settings.HistoryScreen
import io.github.leepy0.strongalarm.ui.settings.LightTestState
import io.github.leepy0.strongalarm.ui.settings.LightsScreen
import io.github.leepy0.strongalarm.ui.settings.LightsUiState
import io.github.leepy0.strongalarm.ui.settings.PermissionItem
import io.github.leepy0.strongalarm.ui.settings.PermissionKey
import io.github.leepy0.strongalarm.ui.settings.PermissionsScreen
import io.github.leepy0.strongalarm.ui.settings.SettingsScreen
import io.github.leepy0.strongalarm.ui.settings.SettingsUiState
import io.github.leepy0.strongalarm.ui.theme.AppTheme
import io.github.leepy0.strongalarm.ui.theme.Palette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * 화면별 스크린샷 (CI에서 PNG로 저장해 디자인 검토용).
 * 기본 크기는 폴드 커버 화면 근사, wide는 펼친 화면 근사
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w384dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** CI에서 받아둔 한글 폰트 (없으면 기본 폰트) */
    @OptIn(ExperimentalTextApi::class)
    private val font: FontFamily? = System.getProperty("screenshot.font")
        ?.let(::File)
        ?.takeIf { it.exists() }
        ?.let { f ->
            FontFamily(
                listOf(300, 400, 500, 600, 700).map { w ->
                    Font(f, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
                },
            )
        }

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            AppTheme(dark = dark, fontFamily = font) {
                Box(Modifier.fillMaxSize().background(Palette.Night)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    // ── 가짜 데이터 ──
    private val today = LocalDate.of(2026, 10, 7)
    /** 2026-10-07 22:45 KST */
    private val NOW = 1791380700000L
    private val seven = LocalTime.of(7, 0)

    private fun day(offset: Long, ring: Boolean, label: String, reason: String, overridden: Boolean = false, time: LocalTime = seven) =
        DayCell(today.plusDays(offset), ring, time, overridden, label, reason)

    private val days = listOf(
        day(0, true, "07:00", "평일이에요"),
        day(1, true, "07:00", "평일이에요"),
        day(2, false, "공휴일", "공휴일이에요: 한글날"),
        day(3, false, "주말", "주말이에요"),
        day(4, false, "주말", "주말이에요"),
        day(5, true, "07:00", "평일이에요"),
        day(6, true, "06:30", "이 날만 시각을 바꿔뒀어요", overridden = true, time = LocalTime.of(6, 30)),
        day(7, true, "07:00", "평일이에요"),
        day(8, false, "연차", "휴무 일정이 있어요: 평영 연차"),
        day(9, false, "여행", "휴무 일정이 있어요: 제주 여행"),
        day(10, true, "07:00", "근무 일정이 있어요: 특근"),
        day(11, false, "주말", "주말이에요"),
        day(12, true, "07:00", "평일이에요"),
        day(13, true, "07:00", "평일이에요"),
    )

    private val home = HomeUiState(
        today = today,
        next = NextAlarm(today.plusDays(1), seven, "평일이에요", ringAt = NOW + (8 * 60 + 15) * 60_000L),
        baseTime = seven,
        days = days,
        ringing = false,
        dimming = false,
        readiness = Readiness(
            missingPermissions = 1,
            watchNodes = 1,
            lightsLinked = 3,
            holidayCalendarSet = true,
            calendarReadable = true,
            lastCheck = NOW - 12 * 60_000,
            lastResult = "30걸음 걸어서 끔",
            lastResultAt = NOW - 26 * 3_600_000,
        ),
        nowMillis = NOW,
    )

    private val appUi = AppVersionUi(installed = "0.2.12", status = "최신이에요 · 5분 전 확인")

    @Composable
    private fun Home(state: HomeUiState) = HomeScreen(state, {}, {}, {}, {}, {}, {}, {})

    private val permissions = listOf(
        PermissionItem(PermissionKey.NOTIFICATIONS, "알림", "알람 화면과 23시 안내를 띄워요", true),
        PermissionItem(PermissionKey.FULL_SCREEN, "잠금화면 알람", "화면이 꺼져 있어도 알람 화면을 띄워요", true),
        PermissionItem(PermissionKey.EXACT_ALARM, "정확한 알람", "정해진 시각에 정확히 울려요", true),
        PermissionItem(PermissionKey.BATTERY, "배터리 최적화 제외", "절전 중에도 디밍·알람이 멈추지 않아요", false),
        PermissionItem(PermissionKey.CALENDAR, "캘린더 읽기", "연차·특근·공휴일 일정을 확인해요", true),
        PermissionItem(PermissionKey.ACTIVITY, "신체 활동", "폰으로도 걸음을 세요", true),
        PermissionItem(PermissionKey.OVERLAY, "다른 앱 위에 표시", "폰을 쓰는 중에도 알림 대신 알람 화면을 바로 띄워요", false, optional = true),
    )

    private val lights = LightsUiState(
        clientId = "a1b2c3", redirectUri = "https://httpbin.org/get", hasSecret = true, loggedIn = true,
        dimmers = listOf("침실 조명", "거실 조명"), switches = listOf("침실 스위치"),
        leadMinutes = 15, switchDelayMinutes = 5, autoOffMinutes = 5,
    )

    // ── 화면 ──
    @Test fun home() = shot("01_home") { Home(home) }

    @Test fun homeRinging() = shot("02_home_ringing") {
        Home(home.copy(ringing = true, readiness = home.readiness.copy(missingPermissions = 0, watchNodes = 0, lightsLinked = null, holidayCalendarSet = false)))
    }

    @Test fun daySheetSkip() = shot("03_day_sheet_skip") {
        Box(Modifier.background(Palette.Dusk).padding(top = 24.dp)) { DaySheetContent(days[8], seven, {}, {}, {}) }
    }

    @Test fun daySheetOverride() = shot("04_day_sheet_override") {
        Box(Modifier.background(Palette.Dusk).padding(top = 24.dp)) { DaySheetContent(days[6], seven, {}, {}, {}) }
    }

    @Test fun rules() = shot("05_rules") {
        RulesScreen(
            RulesUiState(
                rule = DayOffRule(holidayCalendarIds = setOf(9L)),
                calendarNames = mapOf(9L to "대한민국의 휴일", 1L to "개인"),
                overrideCount = 1,
                calendarReadable = true,
            ),
            {}, {}, {}, {},
        )
    }

    @Test fun settings() = shot("06_settings") {
        SettingsScreen(SettingsUiState(stepGoal = 30, alarmVolume = 80, lightsSummary = "3개", missingPermissions = 1, app = appUi), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun lightsConnected() = shot("07_lights") { LightsScreen(lights, {}, { _, _, _ -> }, {}, {}, {}, {}, {}, {}) }

    @Test fun lightsSetup() = shot("08_lights_setup") {
        LightsScreen(lights.copy(loggedIn = false, hasSecret = false, dimmers = emptyList(), switches = emptyList()), {}, { _, _, _ -> }, {}, {}, {}, {}, {}, {})
    }

    @Test fun permissionsScreen() = shot("09_permissions") { PermissionsScreen(permissions, {}, {}) }

    @Test fun history() = shot("10_history") {
        HistoryScreen(
            listOf(
                "10-06 23:00:01 | 안내: 2026-10-07 울림 07:00 — 평일",
                "10-07 06:45:00 | 디밍 시작 → 07:00 (평일)",
                "10-07 07:00:00 | 울림 시작 — 평일",
                "10-07 07:02:41 | 기상 확인 — 걸음 30/30",
                "10-07 07:07:42 | 자동 소등 (3/3)",
            ),
            {}, {},
        )
    }

    @Test fun historyEmpty() = shot("15_history_empty") { HistoryScreen(emptyList(), {}, {}) }

    @Test fun lightsTesting() = shot("14_lights_testing") {
        LightsScreen(lights.copy(test = LightTestState(0.5f)), {}, { _, _, _ -> }, {}, {}, {}, {}, {}, {})
    }

    @Test fun homeProblems() = shot("16_home_problems") {
        Home(
            home.copy(
                next = home.next?.copy(confirmed = true),
                readiness = home.readiness.copy(
                    missingPermissions = 0,
                    lastCheckCalendarOk = false,
                    lightsAuthError = true,
                    newVersion = "0.2.15",
                ),
            ),
        )
    }

    @Test fun alarm() = shot("11_alarm") {
        AlarmScreen(AlarmUiState(LocalTime.of(7, 1), "평일이에요", false, 3, 18, 1, 30, false), {}, {}, {})
    }

    @Test fun alarmSensorProblem() = shot("17_alarm_sensor_problem") {
        AlarmScreen(
            AlarmUiState(LocalTime.of(7, 1), "평일이에요", false, 4, 0, 1, 30, false, sensorNote = "워치: 신체 활동 권한 없음"),
            {}, {}, {},
        )
    }

    @Config(qualifiers = "w384dp-h1500dp-xxhdpi")
    @Test fun settingsUpdate() = shot("18_settings_update") {
        SettingsScreen(
            SettingsUiState(
                stepGoal = 30, alarmVolume = 80, lightsSummary = "3개", missingPermissions = 0,
                app = AppVersionUi(
                    installed = "0.2.12", newVersion = "0.2.15",
                    notes = listOf("자동 업데이트 확인", "홈: 남은 시간 표시, 월~일 달력", "걸음 수 집계 보강"),
                    watchChanged = true,
                ),
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun daySheetConfirm() = shot("19_day_sheet_confirm") {
        Box(Modifier.background(Palette.Dusk).padding(top = 24.dp)) {
            DaySheetContent(days[1], seven, {}, {}, {}, confirmable = true)
        }
    }

    @Test fun alarmLocked() = shot("20_alarm_locked") {
        AlarmScreen(AlarmUiState(LocalTime.of(7, 1), "평일이에요", false, 12, 9, 1, 30, false, restLocked = true), {}, {}, {})
    }

    @Test fun alarmPaused() = shot("12_alarm_paused") {
        AlarmScreen(AlarmUiState(LocalTime.of(7, 1), "평일이에요", false, 0, 0, 0, 30, true), {}, {}, {})
    }

    // ── 밝은 모드 ──
    @Test fun homeLight() = shot("21_home_light", dark = false) { Home(home) }

    @Test fun rulesLight() = shot("22_rules_light", dark = false) {
        RulesScreen(
            RulesUiState(DayOffRule(holidayCalendarIds = setOf(9L)), mapOf(9L to "대한민국의 휴일"), 1, true),
            {}, {}, {}, {},
        )
    }

    @Test fun settingsLight() = shot("23_settings_light", dark = false) {
        SettingsScreen(SettingsUiState(stepGoal = 30, alarmVolume = 80, lightsSummary = "3개", missingPermissions = 1, app = appUi), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun daySheetLight() = shot("24_day_sheet_light", dark = false) {
        Box(Modifier.background(Palette.Dusk).padding(top = 24.dp)) { DaySheetContent(days[8], seven, {}, {}, {}) }
    }

    @Test fun homeLoading() = shot("25_home_loading") { Home(home.copy(days = emptyList(), next = null)) }

    @Config(qualifiers = "w720dp-h800dp-xxhdpi")
    @Test fun homeWide() = shot("13_home_wide") { Home(home) }
}
