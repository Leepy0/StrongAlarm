package io.github.leepy0.strongalarm.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * 차량·자전거 이동 감지 (Google 활동 인식 전환 API).
 * 운전 중엔 걸음도 폰 사용도 없어서 다시 잠듦 감시가 '잠듦'으로 오판하므로, 이동이 감지되면 외출로 본다.
 * 보통 이동 시작 1~3분 뒤 감지됨
 */
class DriveDetector(ctx: Context, private val onMoving: (String) -> Unit) {
    private val appCtx = ctx.applicationContext
    private val pi = PendingIntent.getBroadcast(
        appCtx, 0, Intent(ACTION).setPackage(appCtx.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // 결과를 extras로 채워 넣으므로 MUTABLE
    )
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val enter = ActivityTransitionResult.extractResult(i)?.transitionEvents
                ?.firstOrNull { it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER }
                ?: return
            onMoving(if (enter.activityType == DetectedActivity.ON_BICYCLE) "자전거" else "차량")
        }
    }
    private var running = false

    @SuppressLint("MissingPermission") // 아래에서 확인
    fun start(): Boolean {
        if (appCtx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return false
        appCtx.registerReceiver(receiver, IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED)
        running = true
        val req = ActivityTransitionRequest(
            listOf(DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE).map {
                ActivityTransition.Builder().setActivityType(it)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build()
            },
        )
        ActivityRecognition.getClient(appCtx).requestActivityTransitionUpdates(req, pi)
            .addOnFailureListener { Log.w("DriveDetector", "활동 인식 등록 실패", it) }
        return true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!running) return
        running = false
        runCatching { ActivityRecognition.getClient(appCtx).removeActivityTransitionUpdates(pi) }
        runCatching { appCtx.unregisterReceiver(receiver) }
    }

    private companion object {
        const val ACTION = "io.github.leepy0.strongalarm.DRIVE"
    }
}
