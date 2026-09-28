package io.github.leepy0.strongalarm.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leepy0.strongalarm.alarm.AlarmService
import io.github.leepy0.strongalarm.alarm.AlarmSession
import io.github.leepy0.strongalarm.alarm.AlarmSession.UiPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 잠금화면 위 알람 화면. 해제는 걸음 달성 또는 휴무 버튼(5초)뿐 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 뒤로가기로 닫지 않음
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent { AppTheme { AlarmScreen(onFinish = { finish() }) } }
    }
}

@Composable
private fun AlarmScreen(onFinish: () -> Unit) {
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // 커버 화면처럼 작은 화면에서도 잘리지 않도록 스크롤 허용
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 64.sp)
            Text(if (ui.test) "테스트 알람" else ui.reason, style = MaterialTheme.typography.bodyLarge)

            Text("${ui.steps} / ${ui.goal} 걸음", style = MaterialTheme.typography.headlineMedium)
            LinearProgressIndicator(
                progress = { (ui.steps.toFloat() / ui.goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (ui.watchNodes > 0) "워치 ${ui.watchSteps} · 폰 ${ui.phoneSteps}" else "워치 미연결 · 폰 ${ui.phoneSteps}",
                style = MaterialTheme.typography.bodySmall,
            )

            HoldButton(
                holdMs = 5_000,
                paused = ui.phase == UiPhase.PAUSED,
                onPress = { AlarmService.control(ctx, AlarmService.ACTION_PAUSE) },
                onCancel = { AlarmService.control(ctx, AlarmService.ACTION_RESUME) },
                onComplete = { AlarmService.control(ctx, AlarmService.ACTION_HOLIDAY) },
            )
        }
    }
}

/** 누르는 동안 알람 일시정지, 5초 유지 시 휴무 처리, 중간에 떼면 재개 */
@Composable
private fun HoldButton(
    holdMs: Int,
    paused: Boolean,
    onPress: () -> Unit,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val press by rememberUpdatedState(onPress)
    val cancel by rememberUpdatedState(onCancel)
    val complete by rememberUpdatedState(onComplete)

    Box(
        Modifier
            .size(180.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    press()
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(holdMs, easing = LinearEasing))
                        complete()
                    }
                    tryAwaitRelease()
                    if (!job.isCompleted) {
                        job.cancel()
                        scope.launch { progress.snapTo(0f) }
                        cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 10.dp,
        )
        Text(
            if (paused) "휴무 처리 중…\n계속 누르세요" else "오늘 휴무\n5초 누르기",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
