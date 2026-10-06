package io.github.leepy0.strongalarm.core

/**
 * 걸음 센서 2종 합산.
 * - STEP_DETECTOR: 걸음마다 이벤트 1개 (빠르지만 기기에 따라 늦게 몰아서 오거나 아예 안 옴)
 * - STEP_COUNTER: 부팅 후 누적값 (정확하지만 갱신이 늦거나 화면이 꺼지면 묶여서 옴)
 * 둘 중 큰 값을 걸음 수로 쓴다
 */
class StepFusion {
    var detectorSteps = 0
        private set
    var counterSteps = 0
        private set
    private var counterBase: Float? = null

    val steps: Int get() = maxOf(detectorSteps, counterSteps)

    fun onDetector(): Int {
        detectorSteps++
        return steps
    }

    /**
     * 첫 카운터 값이 늦게 오면 그 사이 걸음이 기준값에 묻히므로,
     * 그때까지 감지기가 센 걸음만큼 기준을 앞당겨 맞춘다
     */
    fun onCounter(total: Float): Int {
        val base = counterBase ?: (total - detectorSteps).also { counterBase = it }
        counterSteps = (total - base).toInt().coerceAtLeast(0)
        return steps
    }
}
