package io.github.leepy0.strongalarm.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** WatchAlarmService → WatchAlarmActivity 상태 공유 */
object WatchState {
    data class State(
        val running: Boolean = false,
        val paused: Boolean = false,
        val steps: Int = 0,
        val goal: Int = 30,
        /** 걸음 센서를 못 쓰는 이유. null = 정상 */
        val sensorProblem: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun update(f: (State) -> State) = _state.update(f)
}
