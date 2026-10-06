package io.github.leepy0.strongalarm.ui

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leepy0.strongalarm.alarm.AlarmService
import io.github.leepy0.strongalarm.alarm.AlarmSession
import io.github.leepy0.strongalarm.alarm.AlarmSession.UiPhase
import io.github.leepy0.strongalarm.ui.alarm.AlarmScreen
import io.github.leepy0.strongalarm.ui.alarm.AlarmUiState
import io.github.leepy0.strongalarm.ui.theme.AppTheme
import kotlinx.coroutines.delay
import java.time.LocalTime

/** 잠금화면 위 알람 화면. 해제는 걸음 달성 또는 쉬는 날 버튼(5초)뿐 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 뒤로가기로 닫지 않음
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent { AppTheme { AlarmRoute(onFinish = { finish() }) } }
    }
}

@Composable
private fun AlarmRoute(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val ui by AlarmSession.state.collectAsStateWithLifecycle()

    LaunchedEffect(ui.phase) {
        if (ui.phase == UiPhase.IDLE || ui.phase == UiPhase.DIMMING) onFinish()
    }

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }

    AlarmScreen(
        state = AlarmUiState(
            now = now,
            reason = ui.reason,
            test = ui.test,
            phoneSteps = ui.phoneSteps,
            watchSteps = ui.watchSteps,
            watchNodes = ui.watchNodes,
            goal = ui.goal,
            paused = ui.phase == UiPhase.PAUSED,
        ),
        onPress = { AlarmService.control(ctx, AlarmService.ACTION_PAUSE) },
        onCancel = { AlarmService.control(ctx, AlarmService.ACTION_RESUME) },
        onComplete = { AlarmService.control(ctx, AlarmService.ACTION_HOLIDAY) },
    )
}
