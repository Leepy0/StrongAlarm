package io.github.leepy0.strongalarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AppSettingsTest {
    private val day = LocalDate.of(2026, 10, 20)
    private val other = LocalDate.of(2026, 10, 21)

    @Test fun skipClearsOverrideAndConfirm() {
        val s = AppSettings().withTime(day, LocalTime.of(6, 30)).withConfirm(day).withSkip(day)
        assertTrue(s.isSkipped(day))
        assertFalse(s.isConfirmed(day))
        assertFalse(day.toString() in s.overrides)
    }

    @Test fun timeChangeClearsSkip() {
        val s = AppSettings().withSkip(day).withTime(day, LocalTime.of(6, 30))
        assertFalse(s.isSkipped(day))
        assertEquals(LocalTime.of(6, 30), s.overrideFor(day))
    }

    @Test fun confirmClearsSkip() {
        val s = AppSettings().withSkip(day).withConfirm(day)
        assertFalse(s.isSkipped(day))
        assertTrue(s.isConfirmed(day))
    }

    @Test fun confirmedOffClearsOverride() {
        val s = AppSettings().withTime(day, LocalTime.of(6, 30)).withConfirmedOff(listOf(day))
        assertNull(s.overrideFor(day))
        assertTrue(day in s.confirmedOffDates)
    }

    @Test fun restoreDayTouchesOnlyThatDay() {
        val before = AppSettings().withSkip(other)
        val current = AppSettings().withSkip(day).withSkip(other)
        val restored = current.restoreDay(day, before)
        assertFalse(restored.isSkipped(day))
        assertTrue(restored.isSkipped(other))
    }

    @Test fun brokenOverrideTextIsIgnored() {
        val s = AppSettings(overrides = mapOf(day.toString() to "not-a-time"))
        assertNull(s.overrideFor(day))
    }
}
