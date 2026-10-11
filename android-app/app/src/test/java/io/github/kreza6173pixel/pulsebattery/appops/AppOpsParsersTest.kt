package io.github.kreza6173pixel.pulsebattery.appops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsParsersTest {

    @Test
    fun readsOpLinesWithTrailingTimes() {
        val out = """
            WAKE_LOCK: ignore; time=+1h2m3s456ms ago; duration=+1m0s0ms
            RUN_IN_BACKGROUND: allow; time=+5m0s0ms ago
            RUN_ANY_IN_BACKGROUND: deny
        """.trimIndent()
        val modes = AppOpsParsers.parseModes(out)
        assertEquals(AppOpMode.IGNORE, modes["WAKE_LOCK"])
        assertEquals(AppOpMode.ALLOW, modes["RUN_IN_BACKGROUND"])
        assertEquals(AppOpMode.DENY, modes["RUN_ANY_IN_BACKGROUND"])
    }

    @Test
    fun unknownModeWordsAreSkippedNotGuessed() {
        val modes = AppOpsParsers.parseModes("WAKE_LOCK: banana; time=+0ms ago")
        assertTrue(modes.isEmpty())
    }

    @Test
    fun noOperationsMeansEverythingIsDefault() {
        val out = AppOpsParsers.NO_OPERATIONS
        assertTrue(AppOpsParsers.isNoOperations(out))
        val modes = AppOpsParsers.parseModes(out)
        assertEquals(AppOpMode.DEFAULT, AppOpsParsers.modeOf(modes, BatteryAppOp.WAKE_LOCK))
    }

    @Test
    fun absentOpReadsAsDefault() {
        val modes = AppOpsParsers.parseModes("RUN_IN_BACKGROUND: allow")
        assertEquals(AppOpMode.DEFAULT, AppOpsParsers.modeOf(modes, BatteryAppOp.WAKE_LOCK))
        assertEquals(AppOpMode.ALLOW, AppOpsParsers.modeOf(modes, BatteryAppOp.RUN_IN_BACKGROUND))
    }

    @Test
    fun firstEntryWinsForARepeatedOp() {
        val out = "WAKE_LOCK: ignore\nWAKE_LOCK: allow"
        assertEquals(AppOpMode.IGNORE, AppOpsParsers.parseModes(out)["WAKE_LOCK"])
    }

    @Test
    fun packageNamesAreValidated() {
        assertTrue(AppOpsParsers.isValidPackage("com.example.app"))
        assertFalse(AppOpsParsers.isValidPackage("com example"))
        assertFalse(AppOpsParsers.isValidPackage("com.example; rm -rf /"))
        assertFalse(AppOpsParsers.isValidPackage("noDotsHere"))
    }

    @Test
    fun parsesPmListOutput() {
        val out = "package:com.example.one\npackage:com.example.two\nbroken line\n"
        assertEquals(listOf("com.example.one", "com.example.two"), AppOpsParsers.parsePackageList(out))
    }
}
