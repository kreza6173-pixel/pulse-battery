package io.github.kreza6173pixel.pulsebattery.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmParserTest {

    /** From the phone, 2026-10-02: `dumpsys alarm | grep -A 12 "Top Alarms"`. */
    private val realLines = listOf(
        "Top Alarms:",
        "+6s376ms running, 304 wakeups, 304 alarms: u0a145:com.google.android.gms",
        "*walarm*:com.google.android.intent.action.GCM_RECONNECT",
        "+5s603ms running, 0 wakeups, 358 alarms: 1000:android",
        "*alarm*:TIME_TICK",
        "+5s224ms running, 193 wakeups, 193 alarms: 1000:android",
        "*walarm*:miui.intent.action.CYCLE_CHECK",
        "+5s5ms running, 0 wakeups, 1 alarms: 1000:android",
        "*alarm*:upload_dark_mode_switch",
        "+4s564ms running, 0 wakeups, 33 alarms: u0a145:com.google.android.gms",
        "*alarm*:com.google.android.gms.gcm.ACTION_CHECK_QUEUE",
        "+2s49ms running, 27 wakeups, 27 alarms: u0a205:com.xiaomi.xmsf",
        "*walarm*:com.xiaomi.push.PING_TIMER",
    )

    @Test
    fun parsesRealOutputWithoutIndentation() {
        check(AlarmParser.parseTopAlarms(realLines.joinToString("\n")))
    }

    @Test
    fun parsesRealOutputWithDumpsysIndentation() {
        val indented = realLines.mapIndexed { i, l ->
            when {
                i == 0 -> "  $l"
                l.startsWith("*") -> "      $l"
                else -> "    $l"
            }
        }
        check(AlarmParser.parseTopAlarms(indented.joinToString("\n")))
    }

    private fun check(parsed: List<TopAlarm>?) {
        val alarms = parsed!!
        assertEquals(6, alarms.size)
        val first = alarms[0]
        assertEquals("6s376ms", first.runningTime)
        assertEquals(304, first.wakeups)
        assertEquals(304, first.alarms)
        assertEquals("u0a145", first.uid)
        assertEquals("com.google.android.gms", first.pkg)
        assertEquals("walarm", first.kind)
        assertTrue(first.isWakeup)
        assertEquals("com.google.android.intent.action.GCM_RECONNECT", first.action)

        val tick = alarms[1]
        assertEquals("android", tick.pkg)
        assertEquals("1000", tick.uid)
        assertFalse(tick.isWakeup)
        assertEquals("TIME_TICK", tick.action)

        assertEquals("com.xiaomi.xmsf", alarms[5].pkg)
        assertEquals("com.xiaomi.push.PING_TIMER", alarms[5].action)
    }

    @Test
    fun stopsAtBlankLineAndNextSection() {
        val text = (realLines + listOf("", "Alarm Stats:", "+1s running, 9 wakeups, 9 alarms: 1000:x.y"))
            .joinToString("\n")
        assertEquals(6, AlarmParser.parseTopAlarms(text)!!.size)
    }

    @Test
    fun grepSeparatorEndsTheBlock() {
        val text = (realLines.take(3) + listOf("--", "+1s running, 9 wakeups, 9 alarms: 1000:x.y"))
            .joinToString("\n")
        assertEquals(1, AlarmParser.parseTopAlarms(text)!!.size)
    }

    @Test
    fun missingHeaderIsNullEmptyTableIsEmpty() {
        assertNull(AlarmParser.parseTopAlarms("Current Alarm Manager state:"))
        assertTrue(AlarmParser.parseTopAlarms("Top Alarms:\n")!!.isEmpty())
    }
}
