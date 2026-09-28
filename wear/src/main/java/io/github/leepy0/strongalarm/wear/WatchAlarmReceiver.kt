package io.github.leepy0.strongalarm.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 백그라운드 FGS 시작이 막혔을 때 정확한 알람을 경유해 서비스 시작 (알람 트리거는 FGS 시작 허용) */
class WatchAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val i = Intent(ctx, WatchAlarmService::class.java)
            .setAction(WatchAlarmService.ACTION_START)
            .putExtras(intent)
        runCatching { ctx.startForegroundService(i) }
    }
}
