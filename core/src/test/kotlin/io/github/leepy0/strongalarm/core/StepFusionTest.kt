package io.github.leepy0.strongalarm.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StepFusionTest {
    @Test fun detectorOnly() {
        val f = StepFusion()
        repeat(5) { f.onDetector() }
        assertEquals(5, f.steps)
    }

    @Test fun counterOnlyStartsFromFirstValue() {
        val f = StepFusion()
        f.onCounter(10_000f)
        assertEquals(0, f.steps)
        f.onCounter(10_012f)
        assertEquals(12, f.steps)
    }

    @Test fun lateCounterAlignsToDetector() {
        // 감지기가 7걸음 센 뒤에야 첫 카운터 값이 옴 → 카운터도 7부터
        val f = StepFusion()
        repeat(7) { f.onDetector() }
        f.onCounter(5_007f)
        assertEquals(7, f.counterSteps)
        f.onCounter(5_030f)
        assertEquals(30, f.steps)
    }

    @Test fun detectorStallsCounterContinues() {
        // 감지기가 멈춰도 카운터가 올라가면 반영
        val f = StepFusion()
        f.onCounter(100f)
        repeat(3) { f.onDetector() }
        f.onCounter(125f)
        assertEquals(25, f.steps)
    }

    @Test fun counterNeverNegative() {
        val f = StepFusion()
        repeat(4) { f.onDetector() }
        f.onCounter(50f)
        f.onCounter(40f) // 비정상 값
        assertEquals(4, f.steps)
    }
}
