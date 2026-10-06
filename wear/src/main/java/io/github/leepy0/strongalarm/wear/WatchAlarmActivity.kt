package io.github.leepy0.strongalarm.wear

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** 워치 알람 화면: 걸음 진행률만 표시 (해제 버튼 없음) */
class WatchAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val text = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WatchState.state.collect { s ->
                    if (!s.running) {
                        finish()
                        return@collect
                    }
                    text.text = if (s.paused) {
                        "휴무 버튼\n누르는 중"
                    } else if (s.sensorProblem != null) {
                        // 워치로는 못 셈 → 폰을 들고 걸어야 함
                        "기상 미션\n\n${s.sensorProblem}\n폰을 들고 걸어주세요"
                    } else {
                        "기상 미션\n\n${s.steps} / ${s.goal}\n걸음"
                    }
                }
            }
        }
    }
}
