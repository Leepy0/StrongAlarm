package io.github.leepy0.strongalarm.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** AlarmService → AlarmActivity UI 상태 (같은 프로세스 공유) */
object AlarmSession {
    enum class UiPhase { IDLE, DIMMING, RINGING, PAUSED }

    data class UiState(
        val phase: UiPhase = UiPhase.IDLE,
        val phoneSteps: Int = 0,
        val watchSteps: Int = 0,
        val goal: Int = 30,
        val watchNodes: Int = 0,
        /** 폰 걸음 센서를 못 쓰는 이유. null = 정상 */
        val phoneSensorProblem: String? = null,
        /** 워치 걸음 센서를 못 쓰는 이유. null = 정상 또는 응답 전 */
        val watchSensorProblem: String? = null,
        val reason: String = "",
        val test: Boolean = false,
        /** 전날 울림 확인한 알람: 쉬는 날 버튼 없음, 걸어야만 꺼짐 */
        val restLocked: Boolean = false,
    ) {
        val steps get() = maxOf(phoneSteps, watchSteps)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    internal fun update(f: (UiState) -> UiState) = _state.update(f)

    internal fun reset() {
        _state.value = UiState()
    }
}
