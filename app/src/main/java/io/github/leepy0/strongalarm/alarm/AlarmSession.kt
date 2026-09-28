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
        val reason: String = "",
        val test: Boolean = false,
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
