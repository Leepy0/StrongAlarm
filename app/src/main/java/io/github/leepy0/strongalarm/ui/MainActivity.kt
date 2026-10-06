package io.github.leepy0.strongalarm.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import io.github.leepy0.strongalarm.alarm.AlarmScheduler
import io.github.leepy0.strongalarm.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    companion object {
        /** 23시 안내 알림의 [시각 변경] → 해당 날짜 시각 선택 */
        const val EXTRA_OVERRIDE_DATE = "override_date"

        /** 새 버전 알림 → 설정 탭 */
        const val EXTRA_OPEN_UPDATE = "open_update"
    }

    private val overrideRequest = mutableStateOf<LocalDate?>(null)
    private val openUpdate = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템 바 아이콘 색은 시스템 다크 모드를 따름
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            AppTheme {
                AppRoot(
                    overrideRequest = overrideRequest.value,
                    onOverrideHandled = { overrideRequest.value = null },
                    openUpdate = openUpdate.value,
                    onOpenUpdateHandled = { openUpdate.value = false },
                )
            }
        }
        lifecycleScope.launch(Dispatchers.IO) { AlarmScheduler.rescheduleAll(applicationContext) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE, false) == true) openUpdate.value = true
        intent?.getStringExtra(EXTRA_OVERRIDE_DATE)?.let {
            overrideRequest.value = runCatching { LocalDate.parse(it) }.getOrNull()
        }
    }
}
