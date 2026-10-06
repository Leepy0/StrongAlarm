package io.github.leepy0.strongalarm.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
    }

    private val overrideRequest = mutableStateOf<LocalDate?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 다크 전용 테마라 시스템 바도 밝은 아이콘
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            AppTheme {
                AppRoot(
                    overrideRequest = overrideRequest.value,
                    onOverrideHandled = { overrideRequest.value = null },
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
        intent?.getStringExtra(EXTRA_OVERRIDE_DATE)?.let {
            overrideRequest.value = runCatching { LocalDate.parse(it) }.getOrNull()
        }
    }
}
