package io.github.leepy0.strongalarm.data

import io.github.leepy0.strongalarm.core.DayOffRule
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class LightSettings(
    /** 디밍 조명 (switchLevel) */
    val dimmerIds: List<String> = emptyList(),
    /** 미해제 시 켤 스위치 (switch) */
    val switchIds: List<String> = emptyList(),
    val deviceLabels: Map<String, String> = emptyMap(),
    /** 알람 몇 분 전부터 디밍 */
    val dimLeadMinutes: Int = 15,
    val dimStepSeconds: Int = 30,
    val dimTargetLevel: Int = 100,
    /** 울림 후 몇 분 동안 미해제면 스위치 점등 */
    val switchDelayMinutes: Int = 5,
    /** 해제 후 몇 분 뒤 자동 소등 */
    val autoOffMinutes: Int = 5,
)

@Serializable
data class SmartThingsConfig(
    val clientId: String = "",
    /** SmartThings CLI로 OAuth 앱 만들 때 등록한 값과 같아야 함. WebView에서 가로채므로 실제 접속되지 않음 */
    val redirectUri: String = "https://httpbin.org/get",
)

@Serializable
data class AppSettings(
    /** 알람은 단일 시각 */
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    /** 일회성 변경: "2026-10-02" → "06:30". 해당 날짜는 판정과 무관하게 그 시각에 울림 */
    val overrides: Map<String, String> = emptyMap(),
    /** 직접 쉬는 날로 지정한 날짜. overrides와 겹치지 않음 */
    val skips: Set<String> = emptySet(),
    /** 전날 '울림 확인'한 날짜 → 그날 알람은 쉬는 날 버튼 없이 걸어야만 꺼짐 */
    val confirmed: Set<String> = emptySet(),
    val rule: DayOffRule = DayOffRule(),
    val stepGoal: Int = 30,
    /** 알람 크기 % (10~100). 처음부터 이 크기로 고정 */
    val alarmVolume: Int = 100,
    val nightlyHour: Int = 23,
    val nightlyMinute: Int = 0,
    val lights: LightSettings = LightSettings(),
    val smartThings: SmartThingsConfig = SmartThingsConfig(),
    val holidayAutoDetected: Boolean = false,
) {
    val baseTime: LocalTime get() = LocalTime.of(alarmHour, alarmMinute)

    fun overrideFor(date: LocalDate): LocalTime? =
        overrides[date.toString()]?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    fun isSkipped(date: LocalDate) = date.toString() in skips
    fun isConfirmed(date: LocalDate) = date.toString() in confirmed

    // ── 날짜별 지정 변경 (서로 겹치지 않게 정리) ──

    /** 그날만 시각 변경 = 울림. 쉬는 날 지정은 해제 */
    fun withTime(date: LocalDate, time: LocalTime) =
        copy(overrides = overrides + (date.toString() to time.toString()), skips = skips - date.toString())

    /** 쉬는 날로 지정. 시각 변경·울림 확인은 해제 */
    fun withSkip(date: LocalDate) = date.toString().let {
        copy(skips = skips + it, overrides = overrides - it, confirmed = confirmed - it)
    }

    fun withoutSkip(date: LocalDate) = copy(skips = skips - date.toString())
    /** 울림 확인. 쉬는 날 지정과 겹치지 않게 지정 해제 */
    fun withConfirm(date: LocalDate) = copy(confirmed = confirmed + date.toString(), skips = skips - date.toString())
    fun withoutConfirm(date: LocalDate) = copy(confirmed = confirmed - date.toString())

    /** 되돌리기: 그 날짜의 지정 상태만 이전 값으로 (다른 날짜·설정은 유지) */
    fun restoreDay(date: LocalDate, from: AppSettings): AppSettings {
        val k = date.toString()
        return copy(
            overrides = from.overrides[k]?.let { overrides + (k to it) } ?: (overrides - k),
            skips = if (k in from.skips) skips + k else skips - k,
            confirmed = if (k in from.confirmed) confirmed + k else confirmed - k,
        )
    }
}

/**
 * 디밍·울림이 진행 중인 날짜는 쉬는 날 지정·확인 취소를 막음
 * (아침의 내가 전날 밤의 결정을 뒤집지 못하게)
 */
fun AppState.dayLocked(date: LocalDate): Boolean =
    session?.let { it.date == date.toString() && it.phase != Phase.DONE } == true

@Serializable
data class CachedEntry(val ring: Boolean, val reason: String)

@Serializable
data class NextPlan(val date: String, val time: String, val reason: String, val ringAt: Long)

@Serializable
data class DeviceSnapshot(val on: Boolean? = null, val level: Int? = null)

@Serializable
enum class Phase { DIMMING, RINGING, DONE }

/** 진행 중인 알람 세션. 조명 복구·자동 소등에 필요한 스냅샷 보관 */
@Serializable
data class SessionState(
    val date: String,
    val ringAt: Long,
    val phase: Phase,
    val snapshot: Map<String, DeviceSnapshot> = emptyMap(),
    val dimmersTouched: Boolean = false,
    val switchesTouched: Boolean = false,
    val test: Boolean = false,
    /** 울림 전 알람 볼륨 (프로세스 재시작·재부팅 후에도 원래 값으로 복구) */
    val originalAlarmVolume: Int? = null,
)

@Serializable
data class AppState(
    /** 날짜별 판정 캐시 (캘린더를 읽을 수 없을 때 사용) */
    val cache: Map<String, CachedEntry> = emptyMap(),
    val next: NextPlan? = null,
    val session: SessionState? = null,
    /** 마지막으로 알람 일정을 다시 계산한 시각 */
    val lastCheck: Long? = null,
    /** 마지막 계산 때 캘린더를 읽었는지 */
    val lastCheckCalendarOk: Boolean = true,
    /** 지난 알람 결과 (예: "07:03 걸어서 끔") */
    val lastResult: String? = null,
    val lastResultAt: Long? = null,
    /** SmartThings 토큰 갱신이 거부됨 → 재로그인 필요 */
    val lightsAuthError: Boolean = false,
    /** 마지막으로 23시 안내(Nightly)가 실행된 시각. 하루 넘게 멈추면 백그라운드 실행이 막힌 것 */
    val lastNightlyAt: Long? = null,
    /** 마지막 업데이트 확인 성공 시각 */
    val updateCheckedAt: Long? = null,
    /** 새 버전 알림을 보낸 마지막 versionCode (같은 버전 중복 알림 방지) */
    val updateNotifiedVersion: Int = 0,
    /** 업데이트 APK 다운로드 id → 기대 sha256 (완료 시 무결성 확인, 업데이트 후 정리) */
    val updateDownloads: Map<String, String> = emptyMap(),
)

object Stores {
    val settings = JsonFileStore("settings.json", AppSettings.serializer()) { AppSettings() }
    val state = JsonFileStore("state.json", AppState.serializer()) { AppState() }
}
