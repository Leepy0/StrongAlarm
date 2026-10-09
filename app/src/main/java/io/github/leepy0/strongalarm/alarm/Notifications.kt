package io.github.leepy0.strongalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.leepy0.strongalarm.core.Judgement
import io.github.leepy0.strongalarm.core.ReasonCode
import io.github.leepy0.strongalarm.data.PendingOffStreak
import io.github.leepy0.strongalarm.data.Stores
import io.github.leepy0.strongalarm.ui.AlarmActivity
import io.github.leepy0.strongalarm.ui.MainActivity
import io.github.leepy0.strongalarm.ui.pretty
import io.github.leepy0.strongalarm.update.UpdateActionReceiver

object Notifications {
    const val CH_RING = "alarm_ring"
    const val CH_PREP = "alarm_prep"
    const val CH_NIGHTLY = "nightly"
    const val CH_UPDATE = "update"
    const val CH_OFF_STREAK = "off_streak"

    const val ID_PREP = 1001
    const val ID_RING = 1002
    const val ID_NIGHTLY = 1003
    const val ID_UPDATE = 1004
    const val ID_FULL_SCREEN = 1005
    const val ID_OFF_STREAK = 1006

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                // 소리·진동은 서비스가 직접 재생하므로 채널은 무음
                NotificationChannel(CH_RING, "알람 울림", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CH_PREP, "알람 준비 (조명 디밍)", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_NIGHTLY, "내일 알람 안내", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_UPDATE, "새 버전 안내", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_OFF_STREAK, "연속 휴무 일정 확인", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun prep(ctx: Context, text: String): Notification =
        Notification.Builder(ctx, CH_PREP)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("알람 준비 중")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(mainIntent(ctx))
            .build()

    fun ringing(ctx: Context, text: String): Notification {
        val full = PendingIntent.getActivity(
            ctx, 10,
            Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(ctx, CH_RING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("기상 미션")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()
    }

    /**
     * 23시 안내: 내일 울림 여부 + 사유.
     * 울리는 날: [울림 확인] [쉬는 날로] [시각 변경] — 확인하면 그날은 쉬는 날 버튼 없이 걸어야만 꺼짐
     * 연속 휴무 일정 확인 전: [울림 확인] [안 울려도 돼요] [시각 변경] — 확인하면 그 일정의 날들은 쉼
     * 쉬는 날: [그래도 울리기] [시각 변경]
     */
    fun showNightly(ctx: Context, j: Judgement) {
        val confirmed = j.ring && Stores.settings.get(ctx).isConfirmed(j.date)
        // 연속 휴무 일정 확인 전인 날이면 그 묶음 전체를 한 번에 확인할 수 있게
        val streak = if (j.code == ReasonCode.OFF_UNCONFIRMED) {
            Stores.state.get(ctx).pendingOff.firstOrNull { j.date.toString() in it.pending }
        } else {
            null
        }
        val title = when {
            !j.ring -> "내일 알람 없음"
            confirmed -> "내일 ${j.time} 알람 · 확인됨"
            streak != null -> "내일 ${j.time} 알람 · 연속 휴무 일정 확인 전"
            else -> "내일 ${j.time} 알람, 울려도 될까요?"
        }
        val base = when {
            !j.ring -> "${j.date.pretty()} · ${j.describe()}"
            confirmed -> "${j.date.pretty()} · ${j.describe()}. 쉬는 날 버튼 없이 걸어야만 꺼져요."
            streak != null -> "${streak.describe()} 동안 안 울려도 되면 확인해주세요. 확인 전엔 평일처럼 울려요."
            else -> "${j.date.pretty()} · ${j.describe()}. 확인하면 아침에 쉬는 날 버튼으로 끌 수 없어요."
        }
        // 울리는 날인데 전체 화면 알림이 꺼져 있으면 경고 (업데이트 후 시스템이 끄는 경우)
        val text = if (j.ring && !canFullScreen(ctx)) "$base\n전체 화면 알림 권한이 꺼져 있어요. 앱에서 다시 켜주세요." else base
        fun broadcast(req: Int, action: String) = PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra(AlarmScheduler.EXTRA_DATE, j.date.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val changeTime = PendingIntent.getActivity(
            ctx, 20,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OVERRIDE_DATE, j.date.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(ctx, CH_NIGHTLY)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(mainIntent(ctx))
        fun action(label: String, pi: PendingIntent) = builder.addAction(Notification.Action.Builder(null, label, pi).build())
        if (j.ring) {
            if (!confirmed) action("울림 확인", broadcast(22, AlarmScheduler.ACTION_CONFIRM))
            if (streak != null) {
                action("안 울려도 돼요", offStreakIntent(ctx, 24, AlarmScheduler.ACTION_CONFIRM_OFF, streak, fromNightly = true))
            } else {
                action("쉬는 날로", broadcast(23, AlarmScheduler.ACTION_SKIP))
            }
        } else {
            action("그래도 울리기", broadcast(21, AlarmScheduler.ACTION_FORCE_RING))
        }
        action("시각 변경", changeTime)
        ctx.getSystemService(NotificationManager::class.java).notify(ID_NIGHTLY, builder.build())
    }

    fun canFullScreen(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /**
     * 연속 휴무 일정을 찾았을 때: 안 울려도 되는지 확인. 확인 전엔 그 날들이 평일처럼 울림
     * [안 울려도 돼요] → 그 날들 쉼, [그래도 울리기] → 그 날들 매일 시각으로 울림(날짜별 지정)
     */
    fun showOffStreak(ctx: Context, s: PendingOffStreak) {
        val text = "${s.describe()} 동안 안 울려도 될까요? 확인 전엔 평일처럼 울려요."
        val n = Notification.Builder(ctx, CH_OFF_STREAK)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("연속 휴무 일정, 안 울려도 될까요?")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(mainIntent(ctx))
            .addAction(Notification.Action.Builder(null, "안 울려도 돼요", offStreakIntent(ctx, 50, AlarmScheduler.ACTION_CONFIRM_OFF, s)).build())
            .addAction(Notification.Action.Builder(null, "그래도 울리기", offStreakIntent(ctx, 51, AlarmScheduler.ACTION_RING_OFF, s)).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_OFF_STREAK, n)
    }

    private fun offStreakIntent(ctx: Context, req: Int, action: String, s: PendingOffStreak, fromNightly: Boolean = false) =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, AlarmReceiver::class.java).setAction(action)
                .putExtra(AlarmScheduler.EXTRA_DATES, s.pending.joinToString(","))
                .putExtra(AlarmScheduler.EXTRA_FROM_NIGHTLY, fromNightly),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 업데이트 후 전체 화면 알림 권한이 꺼졌을 때: 누르면 해당 설정 화면 */
    fun showFullScreenLost(ctx: Context) {
        val settings = PendingIntent.getActivity(
            ctx, 40,
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "업데이트하면 시스템이 이 권한을 꺼요. 꺼져 있으면 잠금화면에 알람 화면이 바로 뜨지 않아요. 눌러서 다시 켜주세요."
        val n = Notification.Builder(ctx, CH_UPDATE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("전체 화면 알림 권한이 꺼졌어요")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(settings)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_FULL_SCREEN, n)
    }

    /** 새 버전 알림: 누르면 앱의 설정 탭(변경 내용), [받기]는 시스템 다운로드로 APK 받기 */
    fun showUpdate(ctx: Context, title: String, text: String) {
        val open = PendingIntent.getActivity(
            ctx, 31,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val download = PendingIntent.getBroadcast(
            ctx, 32,
            Intent(ctx, UpdateActionReceiver::class.java).setAction(UpdateActionReceiver.ACTION_START),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CH_UPDATE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "받기", download).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_UPDATE, n)
    }

    fun mainIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
