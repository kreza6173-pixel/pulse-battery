package io.github.kreza6173pixel.pulsebattery.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeLockParserTest {

    /** Transcribed from the phone: Xiaomi, Android 16, `dumpsys power | grep -i wake_lock`. */
    private val real = listOf(
        "    no_cached_wake_locks=true",
        "  SCREEN_BRIGHT_WAKE_LOCK        'WindowManager/displayId:0' ON_AFTER_RELEASE ACQ=-3s612ms (uid=1000 pid=1960 pkg=android ws=WorkSource{1000 com.android.systemui})",
        "  PARTIAL_WAKE_LOCK              'NotificationManagerService:post:com.leekleak.trafficlight' ACQ=-151ms (uid=1000 pid=1960 pkg=android ws=WorkSource{10660 com.leekleak.trafficlight})",
    ).joinToString("\n")

    @Test
    fun parsesBothRealLinesAndSkipsTheSetting() {
        val locks = WakeLockParser.parse(real)
        assertEquals(2, locks.size)

        val screen = locks[0]
        assertEquals("SCREEN_BRIGHT_WAKE_LOCK", screen.type)
        assertEquals("SCREEN_BRIGHT", screen.shortType)
        assertEquals("WindowManager/displayId:0", screen.tag)
        assertEquals("ON_AFTER_RELEASE", screen.flags)
        assertEquals("-3s612ms", screen.acquired)
        assertEquals("3s612ms", screen.heldFor)
        assertEquals(1000, screen.uid)
        assertEquals(1960, screen.pid)
        assertEquals("android", screen.pkg)
        assertNull(screen.tagPackage)
        assertEquals(listOf("com.android.systemui"), screen.attributedPackages)

        val partial = locks[1]
        assertEquals("PARTIAL_WAKE_LOCK", partial.type)
        assertEquals("NotificationManagerService:post:com.leekleak.trafficlight", partial.tag)
        assertEquals("", partial.flags)
        assertEquals(listOf("com.leekleak.trafficlight"), partial.attributedPackages)
    }

    /** Real tag seen on the phone (2026-10-02), shown as "android" before this fix. */
    @Test
    fun jobSchedulerLockIsAttributedToTheAppInTheTag() {
        val e = WakeLockParser.parseLine(
            "  PARTIAL_WAKE_LOCK  '*job*r/com.android.vending/com.google.android.finsky.scheduler.process.mainimpl.PhoneskyJobServiceMain' ACQ=-22s445ms (uid=1000 pid=1960 pkg=android ws=WorkSource{10123})"
        )!!
        assertEquals("com.android.vending", e.tagPackage)
        assertEquals(listOf("com.android.vending"), e.attributedPackages)
        assertEquals("22s445ms", e.heldFor)
    }

    @Test
    fun syncTagWithoutSuffixLetter() {
        val e = WakeLockParser.parseLine(
            "  PARTIAL_WAKE_LOCK  '*sync*/com.example.mail/account' ACQ=-1s (uid=1000 pid=2 pkg=android)"
        )!!
        assertEquals("com.example.mail", e.tagPackage)
    }

    @Test
    fun withoutWorkSourceFallsBackToPkg() {
        val e = WakeLockParser.parseLine(
            "  PARTIAL_WAKE_LOCK  'sync' ACQ=-2m1s (uid=10123 pid=55 pkg=com.example.app)"
        )!!
        assertEquals("com.example.app", e.pkg)
        assertEquals(listOf("com.example.app"), e.attributedPackages)
        assertEquals(10123, e.uid)
    }

    @Test
    fun extraWordsAfterAcqAreTolerated() {
        val e = WakeLockParser.parseLine(
            "  PARTIAL_WAKE_LOCK  'job' ACQ=-5m LONG (uid=10200 pid=9 pkg=com.foo.bar)"
        )!!
        assertEquals("-5m", e.acquired)
        assertEquals("com.foo.bar", e.pkg)
    }

    @Test
    fun nonMatchingLinesAreIgnored() {
        assertNull(WakeLockParser.parseLine("Wake Locks: size=2"))
        assertNull(WakeLockParser.parseLine("    no_cached_wake_locks=true"))
        assertTrue(WakeLockParser.parse("").isEmpty())
    }
}
