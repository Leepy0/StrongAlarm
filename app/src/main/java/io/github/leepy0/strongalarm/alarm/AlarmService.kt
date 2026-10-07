package io.github.leepy0.strongalarm.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import io.github.leepy0.strongalarm.alarm.AlarmSession.UiPhase
import io.github.leepy0.strongalarm.alarm.AlarmSession.UiState
import io.github.leepy0.strongalarm.core.AlarmPlanner
import io.github.leepy0.strongalarm.core.DimRamp
import io.github.leepy0.strongalarm.core.WearProtocol
import io.github.leepy0.strongalarm.data.AppSettings
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Phase
import io.github.leepy0.strongalarm.data.SessionState
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.lights.LightController
import io.github.leepy0.strongalarm.lights.SmartThingsAuth
import io.github.leepy0.strongalarm.ui.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 알람 세션 전체를 담당하는 포그라운드 서비스 (systemExempted: 정확한 알람 권한 보유 앱의 알람 지속용)
 *
 * IDLE → (PRE) DIMMING → (RING) RINGING ⇄ PAUSED(휴무 버튼 누르는 중) → 걸음 달성 / 휴무 → IDLE
 */
class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        private const val PKG = "io.github.leepy0.strongalarm"
        const val ACTION_PAUSE = "$PKG.PAUSE"
        const val ACTION_RESUME = "$PKG.RESUME"
        const val ACTION_HOLIDAY = "$PKG.HOLIDAY"

        private const val FGS_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED

        /** 서비스 종료 후에도 이어져야 하는 작업 (워치 종료 재전송, 로그) */
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** 리시버(정확한 알람)에서 호출. 울림 시작이 막히면 알림을 띄우고 1분 뒤 다시 시도 (최대 3회) */
        fun start(ctx: Context, action: String, date: String?, retry: Int = 0) {
            val i = Intent(ctx, AlarmService::class.java)
                .setAction(action)
                .putExtra(AlarmScheduler.EXTRA_DATE, date)
            try {
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                Log.e(TAG, "서비스 시작 실패", e)
                appScope.launch { HistoryLog.add(ctx, "서비스 시작 실패 (${retry + 1}회차): $e") }
                if (action == AlarmScheduler.ACTION_RING) {
                    runCatching {
                        ctx.getSystemService(NotificationManager::class.java)
                            .notify(Notifications.ID_RING, Notifications.ringing(ctx, "알람 시작 실패 · 1분 뒤 다시 울려요"))
                    }
                    if (retry < 3) AlarmScheduler.scheduleRingRetry(ctx, date, retry + 1)
                }
            }
        }

        /** 알람 화면(포그라운드)에서 호출 */
        fun control(ctx: Context, action: String) {
            runCatching { ctx.startService(Intent(ctx, AlarmService::class.java).setAction(action)) }
                .onFailure { Log.e(TAG, "제어 실패 $action", it) }
        }
    }

    private enum class Mode { IDLE, DIMMING, RINGING, PAUSED, FINISHING }

    private var mode = Mode.IDLE
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var sound: AlarmSound? = null
    private var stepTracker: StepTracker? = null
    private var dimJob: Job? = null
    private val ringJobs = mutableListOf<Job>()
    private var watchListener: MessageClient.OnMessageReceivedListener? = null
    private var ringStartedAt = 0L
    /** 워치가 알람 시작 확인(WATCH_STATUS)을 보냈는지 */
    private var watchAcked = false
    private var test = false
    private var goal = 30
    /** 전날 울림 확인 → 쉬는 날 버튼 무시 */
    private var restLocked = false
    /** 테스트 알람 종료 처리 중 도착한 실제 알람 → 종료 후 바로 울림 */
    private var pendingRing: LocalDate? = null

    private val vibrator by lazy { getSystemService(VibratorManager::class.java).defaultVibrator }
    private val ctx: Context get() = applicationContext

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val date = intent?.getStringExtra(AlarmScheduler.EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        when (intent?.action) {
            AlarmScheduler.ACTION_PRE -> {
                enterForeground()
                onPre(date)
            }
            AlarmScheduler.ACTION_RING -> {
                enterForeground()
                onRing(date, test = false, judged = false)
            }
            AlarmScheduler.ACTION_TEST -> {
                enterForeground()
                onRing(date, test = true, judged = true)
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_HOLIDAY -> holiday()
            null -> recover()
        }
        if (mode == Mode.IDLE && intent?.action in setOf(ACTION_PAUSE, ACTION_RESUME, ACTION_HOLIDAY)) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        if (mode == Mode.RINGING || mode == Mode.PAUSED) stopRinging()
        dimJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    // ───────── 디밍 ─────────

    private fun onPre(date: LocalDate) {
        if (mode != Mode.IDLE) return
        mode = Mode.DIMMING
        acquireWakeLock()
        scope.launch {
            val j = withContext(Dispatchers.IO) { AlarmScheduler.judge(ctx, date) }
            if (mode != Mode.DIMMING) return@launch // 그 사이 울림 시작됨
            val lights = Stores.settings.get(ctx).lights
            val ringAt = AlarmPlanner.ringAtMillis(j, ZoneId.systemDefault())
            val now = System.currentTimeMillis()
            when {
                !j.ring -> {
                    log("디밍 전 판정: 스킵 — ${j.describe()}")
                    withContext(Dispatchers.IO) { AlarmScheduler.rescheduleAll(ctx) }
                    finishIdle()
                }
                // 일회성 변경 등으로 울림 시각이 바뀐 경우 → 재등록된 PRE에서 처리
                ringAt <= now || ringAt - now > (lights.dimLeadMinutes + 1) * 60_000L -> finishIdle()
                lights.dimmerIds.isEmpty() || !SmartThingsAuth.isLoggedIn(ctx) -> finishIdle()
                else -> startDimming(date, ringAt, j.time.toString(), j.describe())
            }
        }
    }

    private fun startDimming(date: LocalDate, ringAt: Long, timeText: String, reason: String) {
        val lights = Stores.settings.get(ctx).lights
        // 재진입(프로세스 재시작·PRE 재발화)이면 처음 찍은 스냅샷 유지 → 앱이 켠 조명을 '원래 켜진 조명'으로 오인하지 않게
        val existing = Stores.state.get(ctx).session
            ?.takeIf { it.date == date.toString() && it.phase == Phase.DIMMING && it.dimmersTouched }
        if (existing == null) {
            Stores.state.update(ctx) { it.copy(session = SessionState(date.toString(), ringAt, Phase.DIMMING)) }
        }
        notifyPrep("$timeText 알람 · 조명 켜는 중")
        AlarmSession.update { UiState(phase = UiPhase.DIMMING, reason = reason) }
        log("디밍 시작 → $timeText ($reason)")

        dimJob = scope.launch(Dispatchers.IO) {
            val snap = existing?.snapshot
                ?: LightController.snapshot(ctx, lights.dimmerIds + lights.switchIds)
                    .also { s -> updateSession { it.copy(snapshot = s, dimmersTouched = true) } }
            // 꺼져 있던 게 확인된 조명만 (이미 켜져 있거나 상태를 못 읽은 조명은 건드리지 않음)
            val rampIds = lights.dimmerIds.filter { snap[it]?.on == false }
            val rampStart = ringAt - lights.dimLeadMinutes * 60_000L
            while (isActive && System.currentTimeMillis() < ringAt) {
                val level = DimRamp.levelAt(System.currentTimeMillis(), rampStart, ringAt, lights.dimTargetLevel)
                LightController.setLevels(ctx, rampIds, level, turnOn = true)
                delay(lights.dimStepSeconds * 1000L)
            }
        }
    }

    // ───────── 울림 ─────────

    private fun onRing(date: LocalDate, test: Boolean, judged: Boolean) {
        when (mode) {
            // 테스트 알람 정리 중 실제 알람 도착 → 끝나고 바로 울림
            Mode.FINISHING -> {
                if (this.test && !test) pendingRing = date
                return
            }
            // 테스트 알람이 울리는 중 실제 알람 시각 → 실제 알람으로 전환
            Mode.RINGING, Mode.PAUSED -> {
                if (this.test && !test) promoteToReal(date)
                return
            }
            // 디밍 중 테스트 알람은 조명 스냅샷을 망가뜨리므로 무시
            Mode.DIMMING -> if (test) {
                log("디밍 중이라 테스트 알람 무시")
                return
            }
            Mode.IDLE -> Unit
        }
        val wasDimming = mode == Mode.DIMMING
        mode = Mode.RINGING // 중복 시작 방지
        acquireWakeLock()
        scope.launch {
            var reason = "테스트"
            if (!judged) {
                val j = withContext(Dispatchers.IO) { AlarmScheduler.judge(ctx, date) }
                if (!j.ring) {
                    log("울림 직전 판정: 스킵 — ${j.describe()}")
                    dimJob?.cancel()
                    withContext(Dispatchers.IO) {
                        if (wasDimming) {
                            Stores.state.get(ctx).session?.let { LightController.restore(ctx, it) }
                            Stores.state.update(ctx) { it.copy(session = null) }
                        }
                        AlarmScheduler.rescheduleAll(ctx)
                    }
                    finishIdle()
                    return@launch
                }
                reason = j.describe()
            }
            startRinging(date, test, reason)
        }
    }

    private fun startRinging(date: LocalDate, test: Boolean, reason: String) {
        this.test = test
        val settings = Stores.settings.get(ctx)
        goal = settings.stepGoal
        restLocked = !test && settings.isConfirmed(date)
        ringStartedAt = System.currentTimeMillis()
        dimJob?.cancel()

        // 프로세스 재시작으로 다시 울리는 경우: 이미 최대로 올린 볼륨이 아니라 처음 저장한 원래 볼륨으로 복구해야 함
        val savedVolume = if (test) null else Stores.state.get(ctx).session
            ?.takeIf { it.date == date.toString() && it.phase == Phase.RINGING }?.originalAlarmVolume
        val alarmSound = AlarmSound(this, restoreVolume = savedVolume)
        if (!test) beginSession(date, settings, alarmSound.originalVolume)

        AlarmSession.update {
            UiState(phase = UiPhase.RINGING, goal = goal, reason = reason, test = test, restLocked = restLocked)
        }
        try {
            startForeground(Notifications.ID_RING, Notifications.ringing(this, "걸음 0 / $goal"), FGS_TYPE)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 실패", e)
        }
        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_PREP)
        launchActivityIfAllowed()

        // 설정한 크기로 고정 (점점 커지는 방식 없음)
        sound = alarmSound.also {
            it.setVolume(settings.alarmVolume.coerceIn(10, 100) / 100f)
            it.start()
        }
        startVibration()
        stepTracker = StepTracker(this) { n -> onPhoneSteps(n) }.also {
            if (it.start()) {
                log("폰 걸음 센서: ${it.description}")
            } else {
                log("폰 걸음 센서 사용 불가: ${it.description}")
                AlarmSession.update { s -> s.copy(phoneSensorProblem = it.description) }
            }
        }
        watchAcked = false
        registerWatch()

        // 1분이 지나도 걸음이 0이면 원인 파악용 기록
        ringJobs += scope.launch {
            delay(60_000)
            if (AlarmSession.state.value.steps == 0) log("1분째 걸음 0 — ${stepDiagnosis()}")
        }

        if (!test) startSwitchTimer(settings)
        log("울림 시작 — $reason" + if (restLocked) " (전날 확인: 쉬는 날 버튼 잠금)" else "")
    }

    /** 실제 알람 세션 시작: 세션 기록(원래 볼륨 포함), 디밍 마무리, 지난 23시 안내 정리 */
    private fun beginSession(date: LocalDate, settings: AppSettings, originalVolume: Int) {
        Stores.state.update(ctx) { st ->
            val cur = st.session
            val next = if (cur != null && cur.date == date.toString() && cur.phase != Phase.DONE) {
                cur.copy(phase = Phase.RINGING)
            } else {
                SessionState(date.toString(), ringStartedAt, Phase.RINGING)
            }
            st.copy(session = next.copy(originalAlarmVolume = next.originalAlarmVolume ?: originalVolume))
        }
        // 디밍 마지막 단계가 끊겼을 수 있으므로 목표 밝기로 마무리
        Stores.state.get(ctx).session?.takeIf { it.dimmersTouched }?.let { s ->
            val ids = settings.lights.dimmerIds.filter { s.snapshot[it]?.on == false }
            appScope.launch { LightController.setLevels(ctx, ids, settings.lights.dimTargetLevel, turnOn = true) }
        }
        // 오늘 날짜를 대상으로 한 23시 안내([쉬는 날로] 등)는 이제 의미 없음
        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_NIGHTLY)
    }

    /** N분 미해제 → 스위치 점등 (꺼져 있던 게 확인된 스위치만) */
    private fun startSwitchTimer(settings: AppSettings) {
        val lights = settings.lights
        if (lights.switchIds.isEmpty() || !SmartThingsAuth.isLoggedIn(ctx)) return
        ringJobs += scope.launch {
            delay(lights.switchDelayMinutes * 60_000L)
            if (mode != Mode.RINGING && mode != Mode.PAUSED) return@launch
            withContext(Dispatchers.IO) {
                val known = Stores.state.get(ctx).session?.snapshot ?: emptyMap()
                val snap = LightController.snapshot(ctx, lights.switchIds.filter { it !in known })
                updateSession { it.copy(snapshot = it.snapshot + snap, switchesTouched = true) }
                val all = known + snap
                LightController.switchesOn(ctx, lights.switchIds.filter { all[it]?.on == false })
            }
            log("${lights.switchDelayMinutes}분 미해제 → 스위치 점등")
        }
    }

    /** 테스트 알람이 울리는 중 실제 알람 시각이 됨 → 쉬는 날 버튼 잠금·조명·세션을 실제 알람 기준으로 */
    private fun promoteToReal(date: LocalDate) {
        scope.launch {
            val j = withContext(Dispatchers.IO) { AlarmScheduler.judge(ctx, date) }
            if (!j.ring || !test || (mode != Mode.RINGING && mode != Mode.PAUSED)) return@launch
            val settings = Stores.settings.get(ctx)
            test = false
            restLocked = settings.isConfirmed(date)
            // 쉬는 날 버튼을 누르는 중이었고 잠긴 알람이면 다시 울림
            if (restLocked && mode == Mode.PAUSED) resume()
            val vol = sound?.originalVolume
                ?: getSystemService(android.media.AudioManager::class.java).getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            beginSession(date, settings, vol)
            startSwitchTimer(settings)
            AlarmSession.update { it.copy(test = false, restLocked = restLocked, reason = j.describe()) }
            log("테스트 중 실제 알람 시각 → 실제 알람으로 전환 — ${j.describe()}")
        }
    }

    private fun startVibration() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 1000, 500), 0)
        runCatching {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        }
    }

    private fun registerWatch() {
        val listener = MessageClient.OnMessageReceivedListener { ev ->
            when (ev.path) {
                WearProtocol.STEPS -> WearProtocol.decodeInt(ev.data)?.let { n -> scope.launch { onWatchSteps(n) } }
                WearProtocol.WATCH_STATUS -> WearProtocol.decodeText(ev.data)?.let { t -> scope.launch { onWatchStatus(t) } }
            }
        }
        watchListener = listener
        runCatching { Wearable.getMessageClient(this).addListener(listener) }
        scope.launch {
            val n = WatchLink.send(ctx, WearProtocol.START, WearProtocol.encodeInt(goal))
            AlarmSession.update { it.copy(watchNodes = n) }
            if (n == 0) log("워치 미연결 — 폰 걸음만 사용")
        }
    }

    private fun onPhoneSteps(n: Int) {
        AlarmSession.update { it.copy(phoneSteps = n) }
        checkGoal()
    }

    /** 워치 알람 시작 확인. "ok:감지기(wake)+카운터" / "fail:신체 활동 권한 없음" */
    private fun onWatchStatus(text: String) {
        watchAcked = true
        val ok = text.startsWith("ok:")
        val detail = text.substringAfter(':')
        if (ok) {
            log("워치 알람 시작 — 걸음 센서: $detail")
        } else {
            log("워치 걸음 센서 사용 불가: $detail")
            AlarmSession.update { it.copy(watchSensorProblem = detail) }
        }
    }

    /** 걸음이 안 올라갈 때 원인 후보를 한 줄로 */
    private fun stepDiagnosis(): String {
        val ui = AlarmSession.state.value
        val t = stepTracker
        val phone = when {
            t == null -> "폰 센서 없음"
            ui.phoneSensorProblem != null -> "폰 ${ui.phoneSensorProblem}"
            t.firstEventAt == null -> "폰 센서(${t.description}) 이벤트 없음"
            else -> "폰 감지기 ${t.detectorSteps}·카운터 ${t.counterSteps}"
        }
        val watch = when {
            ui.watchNodes == 0 -> "워치 미연결"
            !watchAcked -> "워치 응답 없음(워치 앱 실행·알림 권한 확인)"
            ui.watchSensorProblem != null -> "워치 ${ui.watchSensorProblem}"
            else -> "워치 ${ui.watchSteps}걸음 수신"
        }
        return "$phone, $watch"
    }

    private fun onWatchSteps(n: Int) {
        AlarmSession.update { it.copy(watchSteps = maxOf(it.watchSteps, n)) }
        checkGoal()
    }

    private fun checkGoal() {
        if ((mode == Mode.RINGING || mode == Mode.PAUSED) && AlarmSession.state.value.steps >= goal) {
            dismiss(byHoliday = false)
        }
    }

    // ───────── 휴무 버튼 ─────────

    /** 휴무 버튼을 누르는 동안 소리·진동 일시정지 */
    private fun pause() {
        if (mode != Mode.RINGING || restLocked) return
        mode = Mode.PAUSED
        sound?.pause()
        runCatching { vibrator.cancel() }
        AlarmSession.update { it.copy(phase = UiPhase.PAUSED) }
        appScope.launch { WatchLink.send(ctx, WearProtocol.PAUSE) }
    }

    private fun resume() {
        if (mode != Mode.PAUSED) return
        mode = Mode.RINGING
        sound?.resume()
        startVibration()
        AlarmSession.update { it.copy(phase = UiPhase.RINGING) }
        appScope.launch { WatchLink.send(ctx, WearProtocol.RESUME) }
    }

    private fun holiday() {
        if (mode != Mode.RINGING && mode != Mode.PAUSED) return
        if (restLocked) {
            log("확인된 알람이라 쉬는 날 버튼 무시")
            return
        }
        dismiss(byHoliday = true)
    }

    // ───────── 종료 ─────────

    private fun dismiss(byHoliday: Boolean) {
        if (mode == Mode.FINISHING) return
        mode = Mode.FINISHING
        val ui = AlarmSession.state.value
        val steps = ui.steps
        val breakdown = "폰 ${ui.phoneSteps} · 워치 ${ui.watchSteps}"
        val test = this.test
        stopRinging()

        // 워치 종료: 유실 대비 3회 전송
        appScope.launch {
            repeat(3) {
                WatchLink.send(ctx, WearProtocol.STOP)
                delay(2_000)
            }
        }

        scope.launch {
            try {
                dismissWork(byHoliday, steps, breakdown, test)
            } catch (e: Exception) {
                Log.e(TAG, "종료 처리 오류", e)
                log("종료 처리 오류: $e")
            } finally {
                finishIdle()
            }
        }
    }

    /** 종료 후 정리: 조명(휴무면 즉시 복구, 기상이면 자동 소등 예약)·기록·재등록 */
    private suspend fun dismissWork(byHoliday: Boolean, steps: Int, breakdown: String, test: Boolean) {
        withContext(Dispatchers.IO) {
            val autoOff = Stores.settings.get(ctx).lights.autoOffMinutes
            val sess = Stores.state.get(ctx).session
            if (!test && sess != null) {
                when {
                    byHoliday -> {
                        // 휴무: 조명 즉시 복구
                        LightController.restore(ctx, sess)
                        Stores.state.update(ctx) { it.copy(session = null) }
                    }
                    sess.dimmersTouched || sess.switchesTouched -> {
                        // 기상: N분 뒤 자동 소등
                        Stores.state.update(ctx) { it.copy(session = sess.copy(phase = Phase.DONE)) }
                        AlarmScheduler.scheduleLightsOff(ctx, System.currentTimeMillis() + autoOff * 60_000L)
                    }
                    else -> Stores.state.update(ctx) { it.copy(session = null) }
                }
            }
            val suffix = if (test) " (테스트)" else ""
            val result = if (byHoliday) "쉬는 날 버튼으로 끔" else "${steps}걸음 걸어서 끔"
            HistoryLog.add(ctx, (if (byHoliday) "휴무 버튼으로 종료 ($breakdown)" else "기상 확인 — 걸음 $steps/$goal ($breakdown)") + suffix)
            Stores.state.update(ctx) { it.copy(lastResult = result + suffix, lastResultAt = System.currentTimeMillis()) }
            AlarmScheduler.rescheduleAll(ctx)
        }
    }

    private fun stopRinging() {
        ringJobs.forEach { it.cancel() }
        ringJobs.clear()
        dimJob?.cancel()
        sound?.stop()
        sound = null
        runCatching { vibrator.cancel() }
        stepTracker?.stop()
        stepTracker = null
        watchListener?.let { l -> runCatching { Wearable.getMessageClient(this).removeListener(l) } }
        watchListener = null
        // 알람 화면 닫힘
        AlarmSession.update { it.copy(phase = UiPhase.IDLE) }
    }

    private fun finishIdle() {
        mode = Mode.IDLE
        AlarmSession.reset()
        val pending = pendingRing
        pendingRing = null
        if (pending != null) {
            log("테스트 알람 정리 중 도착한 실제 알람 → 다시 울림")
            onRing(pending, test = false, judged = false)
            return
        }
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 프로세스가 죽었다 재시작된 경우(START_STICKY) 울림 복구 */
    private fun recover() {
        val sess = Stores.state.get(ctx).session
        val today = LocalDate.now().toString()
        when {
            sess != null && sess.phase == Phase.RINGING && sess.date == today -> {
                log("서비스 재시작 → 울림 복구")
                enterForeground()
                onRing(LocalDate.parse(sess.date), test = false, judged = true)
            }
            // 디밍 중 프로세스가 죽었으면 저장된 스냅샷으로 디밍 재개
            sess != null && sess.phase == Phase.DIMMING && sess.date == today && sess.ringAt > System.currentTimeMillis() -> {
                log("서비스 재시작 → 디밍 재개")
                enterForeground()
                mode = Mode.DIMMING
                acquireWakeLock()
                val time = Instant.ofEpochMilli(sess.ringAt).atZone(ZoneId.systemDefault()).toLocalTime()
                startDimming(LocalDate.parse(sess.date), sess.ringAt, time.toString(), "디밍 재개")
            }
            else -> stopSelf()
        }
    }

    // ───────── 유틸 ─────────

    private fun enterForeground() {
        try {
            if (mode == Mode.RINGING || mode == Mode.PAUSED) {
                startForeground(Notifications.ID_RING, Notifications.ringing(this, "기상 미션"), FGS_TYPE)
            } else {
                startForeground(Notifications.ID_PREP, Notifications.prep(this, "알람 확인 중"), FGS_TYPE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 실패", e)
            log("포그라운드 전환 실패: $e")
        }
    }

    private fun notifyPrep(text: String) {
        getSystemService(NotificationManager::class.java).notify(Notifications.ID_PREP, Notifications.prep(this, text))
    }

    /** '다른 앱 위에 표시' 권한이 있으면 알람 화면 직접 실행 (화면 켜진 상태에서도 전체 화면) */
    private fun launchActivityIfAllowed() {
        if (!Settings.canDrawOverlays(this)) return
        runCatching {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun updateSession(f: (SessionState) -> SessionState) {
        Stores.state.update(ctx) { st -> st.session?.let { st.copy(session = f(it)) } ?: st }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StrongAlarm:alarm")
            .apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60_000L)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun log(msg: String) {
        appScope.launch { HistoryLog.add(ctx, msg) }
    }
}
