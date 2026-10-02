package io.github.kreza6173pixel.pulsebattery.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmStatsTotalsTest {

    // Header shape from a real Xiaomi / Android 16 dump, plus lines that must be ignored.
    private val sample = listOf(
        "  1000:com.miui.powerkeeper +1s531ms running, 0 wakeups:",
        "  u0a145:com.example.chat +6s376ms running, 12 wakeups:",
        "  u10a145:com.example.chat +1s0ms running, 3 wakeups:",
        "  u0a88:com.google.android.gms +2m3s4ms running, 41 wakeups:",
        "  +6s376ms running, 12 wakeups, 3 alarms: u0a145:com.example.chat",
        "    +1s531ms 0 wakes 12 alarms, last -1m: *alarm*:com.miui.powerkeeper.ACTION",
    ).joinToString("\n")

    @Test
    fun sumsWakeupsPerPackageAcrossUids() {
        val totals = AlarmParser.parseAlarmStatsTotals(sample)
        assertEquals(15, totals["com.example.chat"])
        assertEquals(41, totals["com.google.android.gms"])
        assertEquals(0, totals["com.miui.powerkeeper"])
        assertEquals(3, totals.size)
    }

    @Test
    fun emptyOrUnrelatedOutputGivesEmptyMap() {
        assertTrue(AlarmParser.parseAlarmStatsTotals("").isEmpty())
        assertTrue(AlarmParser.parseAlarmStatsTotals("Top Alarms:\nnothing here").isEmpty())
    }
}
