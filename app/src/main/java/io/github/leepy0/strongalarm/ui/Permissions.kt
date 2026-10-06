package io.github.leepy0.strongalarm.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import io.github.leepy0.strongalarm.ui.settings.PermissionItem
import io.github.leepy0.strongalarm.ui.settings.PermissionKey

/** 현재 권한 상태 */
fun readPermissions(ctx: Context): List<PermissionItem> {
    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val pm = ctx.getSystemService(PowerManager::class.java)
    val am = ctx.getSystemService(AlarmManager::class.java)
    return listOf(
        PermissionItem(PermissionKey.NOTIFICATIONS, "알림", "알람 화면과 23시 안내를 띄워요", granted(Manifest.permission.POST_NOTIFICATIONS)),
        PermissionItem(PermissionKey.FULL_SCREEN, "잠금화면 알람", "화면이 꺼져 있어도 알람 화면을 띄워요", nm.canUseFullScreenIntent()),
        PermissionItem(PermissionKey.EXACT_ALARM, "정확한 알람", "정해진 시각에 정확히 울려요", am.canScheduleExactAlarms()),
        PermissionItem(PermissionKey.BATTERY, "배터리 최적화 제외", "절전 중에도 디밍·알람이 멈추지 않아요", pm.isIgnoringBatteryOptimizations(ctx.packageName)),
        PermissionItem(PermissionKey.CALENDAR, "캘린더 읽기", "연차·특근·공휴일 일정을 확인해요", granted(Manifest.permission.READ_CALENDAR)),
        PermissionItem(PermissionKey.ACTIVITY, "신체 활동", "폰으로도 걸음을 세요", granted(Manifest.permission.ACTIVITY_RECOGNITION)),
        PermissionItem(
            PermissionKey.OVERLAY, "다른 앱 위에 표시",
            "폰을 쓰는 중에도 알림 대신 알람 화면을 바로 띄워요",
            Settings.canDrawOverlays(ctx), optional = true,
        ),
    )
}

/** 런타임 권한이면 해당 문자열, 설정 화면이면 null */
fun runtimePermissionOf(key: PermissionKey): String? = when (key) {
    PermissionKey.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
    PermissionKey.CALENDAR -> Manifest.permission.READ_CALENDAR
    PermissionKey.ACTIVITY -> Manifest.permission.ACTIVITY_RECOGNITION
    else -> null
}

/** 설정 화면으로 이동. 지원하지 않는 화면이면 앱 정보로 */
fun openPermissionSettings(ctx: Context, key: PermissionKey) {
    val pkg = Uri.parse("package:${ctx.packageName}")
    val action = when (key) {
        PermissionKey.FULL_SCREEN -> Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
        PermissionKey.BATTERY -> Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        PermissionKey.EXACT_ALARM -> Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
        PermissionKey.OVERLAY -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION
        else -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    }
    runCatching { ctx.startActivity(Intent(action, pkg)) }
        .onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)) }
}
