package io.github.kreza6173pixel.pulsebattery.standby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyParsersTest {

    /** From the phone, 2026-10-02: `dumpsys deviceidle whitelist | head -n 25` (excerpt). */
    private val realWhitelist = listOf(
        "system-excidle,com.microsoft.appmanager,10398",
        "system-excidle,com.android.providers.calendar,10090",
        "system-excidle,com.android.updater,6102",
        "system-excidle,com.android.vending,10156",
        "system-excidle,com.google.android.gms,10145",
        "system-excidle,com.android.shell,2000",
        "system-excidle,com.miui.core,10206",
        "system,com.microsoft.appmanager,10398",
        "system,com.android.providers.calendar,10090",
        "system,com.android.updater,6102",
    ).joinToString("\n")

    @Test
    fun whitelistGroupsTypesPerPackage() {
        val wl = StandbyParsers.parseWhitelist(realWhitelist)
        assertEquals(setOf("system-excidle", "system"), wl["com.microsoft.appmanager"])
        assertEquals(setOf("system-excidle"), wl["com.google.android.gms"])
        assertEquals(7, wl.size)
    }

    @Test
    fun userWhitelistFlags() {
        val row = AppStandbyRow("com.example", 10, setOf("user"))
        assertTrue(row.userWhitelisted)
        assertFalse(row.systemWhitelisted)
        val sys = AppStandbyRow("com.google.android.gms", 5, setOf("system-excidle"))
        assertFalse(sys.userWhitelisted)
        assertTrue(sys.systemWhitelisted)
        assertEquals(StandbyBucket.EXEMPTED, sys.bucket)
    }

    /** Real: `am get-standby-bucket com.google.android.gms` printed `5`. */
    @Test
    fun singleBucketFromRealOutput() {
        assertEquals(5, StandbyParsers.parseSingleBucket("5\n"))
        assertEquals(StandbyBucket.EXEMPTED, StandbyBucket.fromCode(5))
        assertNull(StandbyParsers.parseSingleBucket("Error: unknown package"))
    }

    @Test
    fun bucketListLines() {
        val m = StandbyParsers.parseBucketList("com.a.b: 10\n  com.c.d: 40\nnoise\nbad: x")
        assertEquals(mapOf("com.a.b" to 10, "com.c.d" to 40), m)
    }

    @Test
    fun packageList() {
        val p = StandbyParsers.parsePackageList("package:com.a.b\npackage:org.c.d\nWARNING\n")
        assertEquals(listOf("com.a.b", "org.c.d"), p)
    }

    @Test
    fun idleState() {
        assertEquals("ACTIVE", StandbyParsers.parseIdleState("ACTIVE\n"))
        assertEquals("IDLE_MAINTENANCE", StandbyParsers.parseIdleState("IDLE_MAINTENANCE"))
        assertNull(StandbyParsers.parseIdleState("Unknown command"))
    }

    @Test
    fun settableBucketsAreTheFiveAmAccepts() {
        assertEquals(
            listOf("active", "working_set", "frequent", "rare", "restricted"),
            StandbyBucket.SETTABLE.map { it.shellName },
        )
    }

    @Test
    fun packageValidationRejectsShellSyntax() {
        assertTrue(StandbyParsers.isValidPackage("com.xiaomi.xmsf"))
        assertFalse(StandbyParsers.isValidPackage("com.x;reboot"))
        assertFalse(StandbyParsers.isValidPackage("nodots"))
        assertFalse(StandbyParsers.isValidPackage(""))
    }
}
