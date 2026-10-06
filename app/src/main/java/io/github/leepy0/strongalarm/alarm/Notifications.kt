package io.github.leepy0.strongalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.leepy0.strongalarm.core.Judgement
import io.github.leepy0.strongalarm.ui.AlarmActivity
import io.github.leepy0.strongalarm.ui.MainActivity
import io.github.leepy0.strongalarm.ui.pretty
import io.github.leepy0.strongalarm.update.Updater

object Notifications {
    const val CH_RING = "alarm_ring"
    const val CH_PREP = "alarm_prep"
    const val CH_NIGHTLY = "nightly"
    const val CH_UPDATE = "update"

    const val ID_PREP = 1001
    const val ID_RING = 1002
    const val ID_NIGHTLY = 1003
    const val ID_UPDATE = 1004

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

    /** 23시 안내: 내일 울림 여부 + 사유, [시각 변경] [그래도 울리기] */
    fun showNightly(ctx: Context, j: Judgement) {
        val title = if (j.ring) "내일 ${j.time} 알람" else "내일 알람 없음"
        val builder = Notification.Builder(ctx, CH_NIGHTLY)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("${j.date.pretty()} · ${j.describe()}")
            .setAutoCancel(true)
            .setContentIntent(mainIntent(ctx))
            .addAction(
                Notification.Action.Builder(
                    null, "시각 변경",
                    PendingIntent.getActivity(
                        ctx, 20,
                        Intent(ctx, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_OVERRIDE_DATE, j.date.toString())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build(),
            )
        if (!j.ring) {
            builder.addAction(
                Notification.Action.Builder(
                    null, "그래도 울리기",
                    PendingIntent.getBroadcast(
                        ctx, 21,
                        Intent(ctx, AlarmReceiver::class.java)
                            .setAction(AlarmScheduler.ACTION_FORCE_RING)
                            .putExtra(AlarmScheduler.EXTRA_DATE, j.date.toString()),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build(),
            )
        }
        ctx.getSystemService(NotificationManager::class.java).notify(ID_NIGHTLY, builder.build())
    }

    /** 새 버전 알림: 누르면 앱의 설정 탭(변경 내용), [받기]는 브라우저로 APK 다운로드 */
    fun showUpdate(ctx: Context, title: String, text: String) {
        val open = PendingIntent.getActivity(
            ctx, 31,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val download = PendingIntent.getActivity(
            ctx, 32, Updater.downloadIntent(),
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
