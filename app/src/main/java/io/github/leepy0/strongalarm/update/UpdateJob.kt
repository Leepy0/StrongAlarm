package io.github.leepy0.strongalarm.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import io.github.leepy0.strongalarm.alarm.Notifications
import io.github.leepy0.strongalarm.data.Stores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 6시간마다 새 버전 확인 → 새 버전이면 버전마다 한 번 알림 */
class UpdateJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                notifyIfNew(applicationContext)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 7002
        private const val PERIOD_MS = 6 * 60 * 60_000L

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, UpdateJob::class.java))
                .setPeriodic(PERIOD_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // version.json 1KB만 받음
                .setPersisted(true)
                .build()
            runCatching { js.schedule(info) }
        }

        suspend fun notifyIfNew(ctx: Context) {
            val remote = Updater.check(ctx) ?: return
            if (Stores.state.get(ctx).updateNotifiedVersion >= remote.versionCode) return
            Stores.state.update(ctx) { it.copy(updateNotifiedVersion = remote.versionCode) }
            val latest = Updater.notesSince(ctx, remote).firstOrNull()?.s
            Notifications.showUpdate(ctx, "새 버전 ${remote.versionName}", latest ?: "눌러서 변경 내용 보기")
        }
    }
}
