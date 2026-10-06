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
import io.github.leepy0.strongalarm.core.VolumeRamp
import io.github.leepy0.strongalarm.core.WearProtocol
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

        /** 리시버(정확한 알람)에서 호출 */
        fun start(ctx: Context, action: String, date: String?) {
            val i = Intent(ctx, AlarmService::class.java)
                .setAction(action)
                .putExtra(AlarmScheduler.EXTRA_DATE, date)
            try {
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                Log.e(TAG, "서비스 시작 실패", e)
                appScope.launch { HistoryLog.add(ctx, "서비스 시작 실패: $e") }
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
    private var test = false
    private var goal = 30

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
        Stores.state.update(ctx) { it.copy(session = SessionState(date.toString(), ringAt, Phase.DIMMING)) }
        notifyPrep("$timeText 알람 · 조명 켜는 중")
        AlarmSession.update { UiState(phase = UiPhase.DIMMING, reason = reason) }
        log("디밍 시작 → $timeText ($reason)")

        dimJob = scope.launch(Dispatchers.IO) {
            val snap = LightController.snapshot(ctx, lights.dimmerIds + lights.switchIds)
            updateSession { it.copy(snapshot = snap, dimmersTouched = true) }
            // 이미 켜져 있던 조명은 건드리지 않음
            val rampIds = lights.dimmerIds.filter { snap[it]?.on != true }
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
        if (mode == Mode.RINGING || mode == Mode.PAUSED || mode == Mode.FINISHING) return
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
        ringStartedAt = System.currentTimeMillis()
        dimJob?.cancel()

        if (!test) {
            Stores.state.update(ctx) { st ->
                val cur = st.session
                val next = if (cur != null && cur.date == date.toString() && cur.phase != Phase.DONE) {
                    cur.copy(phase = Phase.RINGING)
                } else {
                    SessionState(date.toString(), ringStartedAt, Phase.RINGING)
                }
                st.copy(session = next)
            }
            // 디밍 마지막 단계가 끊겼을 수 있으므로 목표 밝기로 마무리
            Stores.state.get(ctx).session?.takeIf { it.dimmersTouched }?.let { s ->
                val ids = settings.lights.dimmerIds.filter { s.snapshot[it]?.on != true }
                appScope.launch { LightController.setLevels(ctx, ids, settings.lights.dimTargetLevel, turnOn = true) }
            }
        }

        AlarmSession.update { UiState(phase = UiPhase.RINGING, goal = goal, reason = reason, test = test) }
        try {
            startForeground(Notifications.ID_RING, Notifications.ringing(this, "걸음 0 / $goal"), FGS_TYPE)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 실패", e)
        }
        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_PREP)
        launchActivityIfAllowed()

        sound = AlarmSound(this).also {
            it.setVolume(VolumeRamp.fractionAt(0))
            it.start()
        }
        startVibration()
        stepTracker = StepTracker(this) { n -> onPhoneSteps(n) }.also {
            if (!it.start()) log("폰 걸음 센서 사용 불가 (권한 확인)")
        }
        registerWatch()

        // 5분마다 볼륨 상향, 종료 없음
        ringJobs += scope.launch {
            while (isActive) {
                delay(VolumeRamp.INTERVAL_MS)
                sound?.setVolume(VolumeRamp.fractionAt(System.currentTimeMillis() - ringStartedAt))
            }
        }

        // N분 미해제 → 스위치 점등
        val lights = settings.lights
        if (!test && lights.switchIds.isNotEmpty() && SmartThingsAuth.isLoggedIn(ctx)) {
            ringJobs += scope.launch {
                delay(lights.switchDelayMinutes * 60_000L)
                if (mode != Mode.RINGING && mode != Mode.PAUSED) return@launch
                withContext(Dispatchers.IO) {
                    val known = Stores.state.get(ctx).session?.snapshot?.keys ?: emptySet()
                    val snap = LightController.snapshot(ctx, lights.switchIds.filter { it !in known })
                    updateSession { it.copy(snapshot = it.snapshot + snap, switchesTouched = true) }
                    LightController.switchesOn(ctx, lights.switchIds)
                }
                log("${lights.switchDelayMinutes}분 미해제 → 스위치 점등")
            }
        }
        log("울림 시작 — $reason")
    }

    private fun startVibration() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 1000, 500), 0)
        runCatching {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        }
    }

    private fun registerWatch() {
        val listener = MessageClient.OnMessageReceivedListener { ev ->
            if (ev.path == WearProtocol.STEPS) {
                WearProtocol.decodeInt(ev.data)?.let { n -> scope.launch { onWatchSteps(n) } }
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
        if (mode != Mode.RINGING) return
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
        if (mode == Mode.RINGING || mode == Mode.PAUSED) dismiss(byHoliday = true)
    }

    // ───────── 종료 ─────────

    private fun dismiss(byHoliday: Boolean) {
        if (mode == Mode.FINISHING) return
        mode = Mode.FINISHING
        val steps = AlarmSession.state.value.steps
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
                HistoryLog.add(ctx, (if (byHoliday) "휴무 버튼으로 종료" else "기상 확인 — 걸음 $steps/$goal") + suffix)
                Stores.state.update(ctx) { it.copy(lastResult = result + suffix, lastResultAt = System.currentTimeMillis()) }
                AlarmScheduler.rescheduleAll(ctx)
            }
            finishIdle()
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
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 프로세스가 죽었다 재시작된 경우(START_STICKY) 울림 복구 */
    private fun recover() {
        val sess = Stores.state.get(ctx).session
        if (sess != null && sess.phase == Phase.RINGING && sess.date == LocalDate.now().toString()) {
            log("서비스 재시작 → 울림 복구")
            enterForeground()
            onRing(LocalDate.parse(sess.date), test = false, judged = true)
        } else {
            stopSelf()
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
