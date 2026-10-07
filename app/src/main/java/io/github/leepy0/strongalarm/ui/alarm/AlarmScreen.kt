package io.github.leepy0.strongalarm.ui.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.ui.hhmm
import io.github.leepy0.strongalarm.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.LocalTime

data class AlarmUiState(
    val now: LocalTime,
    val reason: String,
    val test: Boolean,
    val phoneSteps: Int,
    val watchSteps: Int,
    val watchNodes: Int,
    val goal: Int,
    val paused: Boolean,
    /** 걸음 센서 문제 안내 (예: "워치: 신체 활동 권한 없음"). null = 정상 */
    val sensorNote: String? = null,
    /** 전날 울림 확인 → 쉬는 날 버튼 대신 안내만 */
    val restLocked: Boolean = false,
) {
    val steps get() = maxOf(phoneSteps, watchSteps)
}

/**
 * 알람 화면. 핵심 상태 = 걸음 진행, 주요 행동 = 쉬는 날 버튼(화면 아래 엄지 영역).
 * 화면이 낮으면(커버 화면 가로 등) 스크롤로 전환
 */
@Composable
fun AlarmScreen(state: AlarmUiState, onPress: () -> Unit, onCancel: () -> Unit, onComplete: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Palette.Night)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val tall = maxHeight >= 600.dp
        Column(
            Modifier
                .fillMaxSize()
                .then(if (tall) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.now.hhmm(), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            Text(
                if (state.test) "테스트 알람" else "일어날 시간이에요",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Mist,
            )
            Gap(tall, 32)
            StepRing(state.steps, state.goal)
            Spacer(Modifier.height(16.dp))
            Text(
                if (state.watchNodes > 0) {
                    "워치 ${state.watchSteps}걸음, 폰 ${state.phoneSteps}걸음"
                } else {
                    "워치 연결 안 됨, 폰을 들고 걸어주세요"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Mist,
                textAlign = TextAlign.Center,
            )
            state.sensorNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ember,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Gap(tall, 48)
            if (state.restLocked) RestLocked() else HoldToRest(state.paused, onPress, onCancel, onComplete)
        }
    }
}

/** 키 큰 화면에선 남는 공간을 나눠 갖고, 낮은 화면에선 고정 간격 */
@Composable
private fun ColumnScope.Gap(tall: Boolean, fixed: Int) {
    if (tall) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(fixed.dp))
}

@Composable
private fun StepRing(steps: Int, goal: Int) {
    val target = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, tween(400), label = "steps")
    val track = Palette.Line
    val fill = Palette.Sun
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(fill, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$steps", style = MaterialTheme.typography.displayLarge, color = Palette.Ink)
            Text("/ $goal 걸음", style = MaterialTheme.typography.bodyLarge, color = Palette.Mist)
        }
    }
}

/** 전날 울림을 확인한 알람: 쉬는 날 버튼 자리에 이유만 표시 (버튼처럼 보이지 않게 테두리 없는 글자) */
@Composable
private fun RestLocked() {
    Column(
        Modifier.fillMaxWidth().height(64.dp + 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "전날 울림을 확인한 알람이에요",
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            "쉬는 날 버튼 없이 걸어야만 꺼져요",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Mist,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 쉬는 날 버튼: 누르는 동안 알람 일시정지, 5초 유지하면 종료, 중간에 떼면 다시 울림.
 * 알람을 끄는 위험 동작이라 길게 누르기로 보호
 */
@Composable
private fun HoldToRest(paused: Boolean, onPress: () -> Unit, onCancel: () -> Unit, onComplete: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val press by rememberUpdatedState(onPress)
    val cancel by rememberUpdatedState(onCancel)
    val complete by rememberUpdatedState(onComplete)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(CircleShape)
                .background(Palette.Dusk)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        press()
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
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
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(Palette.MoonSoft),
            )
            Text(
                if (paused) "손을 떼면 다시 울려요" else "쉬는 날이면 5초 누르기",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Moon,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "걸음을 채우면 자동으로 꺼져요",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Mist,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private const val HOLD_MS = 5_000
