package io.github.leepy0.strongalarm.alarm

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 캘린더 변경 시 알람 재계산 (content URI 트리거, 1회성이라 실행 후 재등록) */
class CalendarChangeJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        running = true
        scope.launch {
            try {
                runCatching { AlarmScheduler.rescheduleAll(applicationContext) }
            } finally {
                jobFinished(params, false)
                running = false
                schedule(applicationContext, force = true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running = false
        return true
    }

    override fun onDestroy() {
        // 코루틴이 시작 전에 취소되면 finally가 안 돌아 running이 남을 수 있음
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 7001

        /** 실행 중 같은 ID로 schedule하면 실행 중인 작업이 취소되므로 막음 */
        @Volatile
        private var running = false

        fun schedule(ctx: Context, force: Boolean = false) {
            if (running) return
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (!force && js.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, CalendarChangeJob::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        CalendarContract.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    ),
                )
                .setTriggerContentUpdateDelay(5_000)
                .setTriggerContentMaxDelay(60_000)
                .build()
            runCatching { js.schedule(info) }
        }
    }
}
