package io.github.leepy0.strongalarm.wear

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.google.android.gms.wearable.Wearable
import io.github.leepy0.strongalarm.core.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 워치 알람: 진동 + 걸음 수를 폰으로 전송.
 * 폰의 STOP 수신, 목표 걸음 달성, 60분 경과 중 하나로 종료
 */
class WatchAlarmService : Service() {

    companion object {
        private const val TAG = "WatchAlarmService"
        private const val PKG = "io.github.leepy0.strongalarm.wear"
        const val ACTION_START = "$PKG.START"
        const val ACTION_PAUSE = "$PKG.PAUSE"
        const val ACTION_RESUME = "$PKG.RESUME"
        const val ACTION_STOP = "$PKG.STOP"
        const val EXTRA_NODE = "node"
        const val EXTRA_GOAL = "goal"
        /** START를 받은 시각 (그 뒤에 STOP이 왔으면 시작하지 않음) */
        const val EXTRA_RECEIVED_AT = "receivedAt"
        private const val PREFS = "watch_alarm"
        private const val KEY_STOP_AT = "stopAt"
        private const val REQ_FALLBACK = 1

        private const val CHANNEL = "watch_alarm"
        private const val NOTIF_ID = 2001
        private const val MAX_RUN_MS = 60 * 60_000L

        fun startFromBackground(ctx: Context, nodeId: String, goal: Int) {
            val receivedAt = System.currentTimeMillis()
            val i = Intent(ctx, WatchAlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_NODE, nodeId)
                .putExtra(EXTRA_GOAL, goal)
                .putExtra(EXTRA_RECEIVED_AT, receivedAt)
            try {
                ctx.startForegroundService(i)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException → 정확한 알람 경유
                Log.w(TAG, "직접 시작 불가 → 알람 경유", e)
                val pi = PendingIntent.getBroadcast(
                    ctx, REQ_FALLBACK,
                    Intent(ctx, WatchAlarmReceiver::class.java)
                        .putExtra(EXTRA_NODE, nodeId)
                        .putExtra(EXTRA_GOAL, goal)
                        .putExtra(EXTRA_RECEIVED_AT, receivedAt),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                runCatching {
                    ctx.getSystemService(AlarmManager::class.java)
                        .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 500, pi)
                }
            }
        }

        /**
         * 폰의 STOP: 실행 중이면 종료, 아직 시작 대기 중(알람 경유)이면 예약 취소 +
         * STOP 시각 기록 → 뒤늦게 시작돼도 바로 끝냄 (폰은 꺼졌는데 워치만 60분 진동하는 문제 방지)
         */
        fun stopRequested(ctx: Context) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_STOP_AT, System.currentTimeMillis()).apply()
            PendingIntent.getBroadcast(
                ctx, REQ_FALLBACK, Intent(ctx, WatchAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { pi ->
                runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(pi) }
                pi.cancel()
            }
            control(ctx, ACTION_STOP)
        }

        private fun stoppedSince(ctx: Context, receivedAt: Long): Boolean =
            receivedAt > 0 && ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_STOP_AT, 0) >= receivedAt

        fun control(ctx: Context, action: String) {
            if (!WatchState.state.value.running) return
            runCatching { ctx.startService(Intent(ctx, WatchAlarmService::class.java).setAction(action)) }
                .onFailure { Log.w(TAG, "제어 실패 $action", it) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val vibrator by lazy { getSystemService(VibratorManager::class.java).defaultVibrator }
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutJob: Job? = null
    private var nodeId: String? = null
    private var goal = 30
    private var steps = 0
    private var tracker: WatchStepTracker? = null
    /** 마지막으로 폰에 보낸 시작 확인 문장 (재전송용) */
    private var statusText: String? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                enterForeground()
                if (!running && stoppedSince(this, intent.getLongExtra(EXTRA_RECEIVED_AT, 0))) {
                    // 시작 대기 중에 폰이 이미 알람을 끔
                    Log.i(TAG, "시작 전에 STOP 받음 → 시작 안 함")
                    finish()
                    return START_NOT_STICKY
                }
                nodeId = intent.getStringExtra(EXTRA_NODE) ?: nodeId
                goal = intent.getIntExtra(EXTRA_GOAL, goal)
                if (!running) {
                    begin()
                } else {
                    // 폰이 서비스 재시작 후 START를 다시 보낸 경우: 시작 확인을 다시 보내 폰 진단이 '응답 없음'으로 남지 않게
                    statusText?.let { send(WearProtocol.WATCH_STATUS, WearProtocol.encodeText(it)) }
                }
            }
            ACTION_PAUSE -> if (running) {
                runCatching { vibrator.cancel() }
                WatchState.update { it.copy(paused = true) }
            }
            ACTION_RESUME -> if (running) {
                vibrate()
                WatchState.update { it.copy(paused = false) }
            }
            ACTION_STOP -> finish()
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running) cleanup()
        scope.cancel()
        super.onDestroy()
    }

    private fun begin() {
        running = true
        steps = 0
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StrongAlarm:watch")
            .apply { acquire(MAX_RUN_MS + 60_000L) }
        WatchState.update { WatchState.State(running = true, steps = 0, goal = goal) }
        vibrate()
        startSteps()
        timeoutJob = scope.launch {
            delay(MAX_RUN_MS)
            finish()
        }
    }

    private fun vibrate() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0)
        runCatching {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        }
    }

    private fun startSteps() {
        val t = WatchStepTracker(this) { n -> onSteps(n) }
        tracker = t
        val ok = t.start()
        if (!ok) {
            Log.w(TAG, "걸음 센서 사용 불가: ${t.description}")
            WatchState.update { it.copy(sensorProblem = t.description) }
        }
        // 폰에 시작 확인 + 센서 상태 전송 (폰 기록에 남겨 원인 파악용)
        statusText = (if (ok) "ok:" else "fail:") + t.description
        send(WearProtocol.WATCH_STATUS, WearProtocol.encodeText(statusText!!))
    }

    private fun onSteps(n: Int) {
        if (!running || n == steps) return
        steps = n
        WatchState.update { it.copy(steps = steps) }
        send(WearProtocol.STEPS, WearProtocol.encodeInt(steps))
        if (steps >= goal) finish()
    }

    private fun send(path: String, payload: ByteArray) {
        val node = nodeId ?: return
        Wearable.getMessageClient(this)
            .sendMessage(node, path, payload)
            .addOnFailureListener { Log.w(TAG, "전송 실패 $path", it) }
    }

    private fun finish() {
        if (running) cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        running = false
        timeoutJob?.cancel()
        runCatching { vibrator.cancel() }
        tracker?.stop()
        tracker = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        WatchState.update { WatchState.State() }
    }

    private fun enterForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "기상 미션", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        val full = PendingIntent.getActivity(
            this, 0,
            Intent(this, WatchAlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("기상 미션")
            .setContentText("걸어서 알람 해제")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 실패", e)
        }
    }
}
