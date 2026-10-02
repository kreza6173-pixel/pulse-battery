package io.github.kreza6173pixel.pulsebattery.drain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrainReportTest {

    private val hour = 3_600_000L

    @Test
    fun encodeDecodeRoundTrip() {
        val b = Baseline(1_000L, 50_000L, linkedMapOf("com.a.b" to 3, "com.c.d" to 0))
        assertEquals(b, DrainReport.decode(DrainReport.encode(b)))
    }

    @Test
    fun decodeRejectsGarbage() {
        assertNull(DrainReport.decode(null))
        assertNull(DrainReport.decode(""))
        assertNull(DrainReport.decode("abc"))
        assertNull(DrainReport.decode("12"))
    }

    @Test
    fun deltasAndRates() {
        val b = Baseline(0L, 1_000L, mapOf("com.a.b" to 10, "com.b.c" to 5))
        val now = mapOf("com.a.b" to 25, "com.b.c" to 5, "com.c.d" to 4)
        val r = DrainReport.compute(b, now, 2 * hour, 2 * hour + 1_000L)
        assertFalse(r.rebooted)
        assertEquals(19, r.total)
        assertEquals(listOf("com.a.b", "com.c.d"), r.offenders.map { it.pkg })
        assertEquals(15, r.offenders[0].wakeups)
        assertEquals(7.5, r.offenders[0].perHour!!, 1e-9)
    }

    @Test
    fun droppedCounterCountsAsNew() {
        val b = Baseline(0L, 1_000L, mapOf("com.a.b" to 10))
        val r = DrainReport.compute(b, mapOf("com.a.b" to 3), hour, 2_000L)
        assertEquals(3, r.offenders.single().wakeups)
    }

    @Test
    fun rebootIgnoresOldBaseline() {
        val b = Baseline(0L, 9_000_000L, mapOf("com.a.b" to 2))
        val r = DrainReport.compute(b, mapOf("com.a.b" to 5), hour, 1_000L)
        assertTrue(r.rebooted)
        assertEquals(5, r.offenders.single().wakeups)
    }

    @Test
    fun shortPeriodHasNoRate() {
        val b = Baseline(0L, 0L, emptyMap())
        val r = DrainReport.compute(b, mapOf("com.a.b" to 5), 60_000L, 60_000L)
        assertNull(r.offenders.single().perHour)
    }

    @Test
    fun noBaselineMeansSinceBoot() {
        val r = DrainReport.compute(null, mapOf("com.a.b" to 5), hour, hour)
        assertNull(r.elapsedMs)
        assertEquals(5, r.total)
    }

    @Test
    fun formatsDuration() {
        assertEquals("2h 5m", DrainReport.formatDuration(2 * hour + 5 * 60_000L))
        assertEquals("45m", DrainReport.formatDuration(45 * 60_000L))
    }
}
