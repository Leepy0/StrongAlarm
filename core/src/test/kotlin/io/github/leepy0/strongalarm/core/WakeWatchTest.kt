package io.github.leepy0.strongalarm.core

import io.github.leepy0.strongalarm.core.WakeWatch.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeWatchTest {
    private val min = 60_000L

    @Test fun waitsUntilIdleLongEnough() {
        val w = WakeWatch(startAt = 0, idleMs = 10 * min)
        assertEquals(Verdict.WAIT, w.check(9 * min))
        assertEquals(Verdict.RING, w.check(10 * min))
    }

    @Test fun activityResetsTheIdleClock() {
        val w = WakeWatch(startAt = 0, idleMs = 10 * min)
        w.onActive(8 * min)
        assertEquals(Verdict.WAIT, w.check(17 * min))
        assertEquals(Verdict.RING, w.check(18 * min))
    }

    @Test fun staleActivityDoesNotMoveClockBack() {
        val w = WakeWatch(startAt = 0, idleMs = 10 * min)
        w.onActive(8 * min)
        w.onActive(3 * min)
        assertEquals(8 * min, w.lastActiveAt)
    }

    @Test fun expiresAfterWindowEvenIfIdle() {
        val w = WakeWatch(startAt = 0, idleMs = 10 * min, windowMs = 60 * min)
        assertEquals(Verdict.RING, w.check(59 * min))
        assertEquals(Verdict.EXPIRED, w.check(60 * min))
    }

    @Test fun keepsWatchingWhileActiveInsideWindow() {
        val w = WakeWatch(startAt = 0, idleMs = 10 * min, windowMs = 60 * min)
        for (t in 5..55 step 5) w.onActive(t * min)
        assertEquals(Verdict.WAIT, w.check(59 * min))
    }
}
