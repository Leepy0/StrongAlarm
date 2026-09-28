package io.github.leepy0.strongalarm.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** 폰 걸음 수 (시작 이후 누적). STEP_DETECTOR 우선, 없으면 STEP_COUNTER 차분 */
class StepTracker(ctx: Context, private val onSteps: (Int) -> Unit) : SensorEventListener {
    private val appCtx = ctx.applicationContext
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private var count = 0
    private var base = -1f
    private var useCounter = false

    fun start(): Boolean {
        if (appCtx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val sensor = detector ?: sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return false
        useCounter = detector == null
        return sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        if (useCounter) {
            if (base < 0) base = e.values[0]
            count = (e.values[0] - base).toInt()
        } else {
            count++
        }
        onSteps(count)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
