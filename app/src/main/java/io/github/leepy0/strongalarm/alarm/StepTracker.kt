package io.github.leepy0.strongalarm.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import io.github.leepy0.strongalarm.core.StepFusion

/**
 * 걸음 수 (시작 이후 누적).
 * - STEP_DETECTOR와 STEP_COUNTER를 둘 다 등록하고 큰 값 사용 (삼성 기기는 한쪽이 늦거나 멈추는 경우가 있음)
 * - wake-up 센서가 있으면 우선 사용: 화면이 꺼져도 센서 허브에 묶이지 않고 바로 전달
 * - 2초마다 flush: 센서 허브 FIFO에 쌓인 걸음을 강제로 꺼냄
 * ※ 워치 모듈 WatchStepTracker와 같은 로직
 */
class StepTracker(ctx: Context, private val onSteps: (Int) -> Unit) : SensorEventListener {
    private val appCtx = ctx.applicationContext
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val fusion = StepFusion()
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    /** 등록 결과 (진단 로그용). 예: "감지기(wake)+카운터" */
    var description = "시작 전"
        private set

    /** 첫 센서 이벤트를 받은 시각. null = 아직 없음 */
    var firstEventAt: Long? = null
        private set

    val detectorSteps get() = fusion.detectorSteps
    val counterSteps get() = fusion.counterSteps

    private val flushTick = object : Runnable {
        override fun run() {
            if (!running) return
            sm.flush(this@StepTracker)
            handler.postDelayed(this, FLUSH_MS)
        }
    }

    fun start(): Boolean {
        if (appCtx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            description = "신체 활동 권한 없음"
            return false
        }
        val registered = listOf(Sensor.TYPE_STEP_DETECTOR, Sensor.TYPE_STEP_COUNTER).mapNotNull { type ->
            // wake-up 우선, 등록 실패하면 일반 센서로 재시도.
            // 걸음 센서는 이벤트 방식이라 주기는 의미 없음. 배치 지연 0 = 묶지 말고 바로 전달
            listOfNotNull(sm.getDefaultSensor(type, true), sm.getDefaultSensor(type)).distinct()
                .firstOrNull { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 0) }
        }
        description = if (registered.isEmpty()) {
            "걸음 센서 없음"
        } else {
            registered.joinToString("+") { s ->
                (if (s.type == Sensor.TYPE_STEP_DETECTOR) "감지기" else "카운터") + if (s.isWakeUpSensor) "(wake)" else ""
            }
        }
        if (registered.isEmpty()) return false
        running = true
        handler.postDelayed(flushTick, FLUSH_MS)
        return true
    }

    fun stop() {
        running = false
        handler.removeCallbacks(flushTick)
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (!running) return
        if (firstEventAt == null) firstEventAt = System.currentTimeMillis()
        val n = when (e.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> fusion.onDetector()
            Sensor.TYPE_STEP_COUNTER -> fusion.onCounter(e.values[0])
            else -> return
        }
        onSteps(n)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val FLUSH_MS = 2_000L
    }
}
