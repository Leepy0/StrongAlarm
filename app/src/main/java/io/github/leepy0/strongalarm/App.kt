package io.github.leepy0.strongalarm

import android.app.Application
import io.github.leepy0.strongalarm.alarm.Notifications

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 잠금 해제 전(Direct Boot)에도 실행되므로 CE 저장소 접근 금지
        Notifications.createChannels(this)
    }
}
