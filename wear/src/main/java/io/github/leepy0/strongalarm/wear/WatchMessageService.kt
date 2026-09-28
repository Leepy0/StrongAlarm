package io.github.leepy0.strongalarm.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import io.github.leepy0.strongalarm.core.WearProtocol

/** 폰에서 오는 알람 제어 메시지 수신 */
class WatchMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearProtocol.START -> WatchAlarmService.startFromBackground(
                this,
                nodeId = event.sourceNodeId,
                goal = WearProtocol.decodeInt(event.data) ?: 30,
            )
            WearProtocol.PAUSE -> WatchAlarmService.control(this, WatchAlarmService.ACTION_PAUSE)
            WearProtocol.RESUME -> WatchAlarmService.control(this, WatchAlarmService.ACTION_RESUME)
            WearProtocol.STOP -> WatchAlarmService.control(this, WatchAlarmService.ACTION_STOP)
        }
    }
}
