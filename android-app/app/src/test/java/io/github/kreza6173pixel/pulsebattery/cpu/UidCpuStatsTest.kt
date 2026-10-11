package io.github.kreza6173pixel.pulsebattery.cpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UidCpuStatsTest {

    /** Shape of the driver output: uid, then accumulated user and system time. */
    private val stats = """
        0: 120000000 40000000
        1000: 60000000 30000000
        10176: 5000000 1000000
        10449: 0 0
        10093: 900000 100000
    """.trimIndent()

    private val packages = """
        package:com.android.settings uid:1000
        package:com.google.android.apps.docs uid:10176
        package:com.android.providers.downloads uid:10093
        package:com.android.mtp uid:10093
        package:com.example.idle uid:10449
    """.trimIndent()

    @Test
    fun everyDriverLineIsParsed() {
        val rows = UidCpuParsers.parseStats(stats)
        assertEquals(5, rows.size)
        assertEquals(0, rows[0].first)
        assertEquals(120000000L, rows[0].second)
        assertEquals(40000000L, rows[0].third)
    }

    @Test
    fun linesInAnotherShapeAreSkipped() {
        val rows = UidCpuParsers.parseStats("garbage\n10: abc def\n11: 1 2\n12: 3")
        assertEquals(1, rows.size)
        assertEquals(11, rows[0].first)
    }

    @Test
    fun aRefusedReadParsesAsNothing() {
        val rows = UidCpuParsers.parseStats("cat: /proc/uid_cputime/show_uid_stat: Permission denied")
        assertTrue(rows.isEmpty())
    }

    @Test
    fun theUidMappingIsParsed() {
        val map = UidCpuParsers.parseUidPackages(packages)
        assertEquals(listOf("com.android.settings"), map[1000])
        assertEquals(listOf("com.google.android.apps.docs"), map[10176])
    }

    @Test
    fun severalPackagesOnOneUidAreAllKept() {
        val map = UidCpuParsers.parseUidPackages(packages)
        assertEquals(
            listOf("com.android.providers.downloads", "com.android.mtp"),
            map[10093],
        )
    }

    @Test
    fun aMappingLineInAnotherShapeIsSkipped() {
        val map = UidCpuParsers.parseUidPackages("package:com.x\npackage:com.y uid:abc\n")
        assertTrue(map.isEmpty())
    }

    @Test
    fun rowsAreSortedByTotalTime() {
        val report = buildUidCpuReport(stats, packages)
        assertEquals(0, report.rows[0].uid)
        assertEquals(1000, report.rows[1].uid)
        assertEquals(10176, report.rows[2].uid)
    }

    @Test
    fun uidsWithNoRecordedTimeAreDropped() {
        val report = buildUidCpuReport(stats, packages)
        assertTrue(report.rows.none { it.uid == 10449 })
        assertEquals(4, report.rows.size)
    }

    @Test
    fun theTotalIsTheSumOfEveryCountedRow() {
        val report = buildUidCpuReport(stats, packages)
        assertEquals(160000000L + 90000000L + 6000000L + 1000000L, report.totalMicros)
    }

    @Test
    fun packageNamesAreAttachedToTheirUid() {
        val report = buildUidCpuReport(stats, packages)
        val docs = report.rows.first { it.uid == 10176 }
        assertEquals(listOf("com.google.android.apps.docs"), docs.packages)
    }

    @Test
    fun aUidWithNoKnownPackageStillAppears() {
        val report = buildUidCpuReport(stats, "")
        assertTrue(report.rows.first().packages.isEmpty())
        assertEquals(4, report.rows.size)
    }

    @Test
    fun theTailIsSummarisedBeyondTheCap() {
        val report = buildUidCpuReport(stats, packages, cap = 2)
        assertEquals(2, report.rows.size)
        assertEquals(2, report.hiddenRows)
        assertEquals(6000000L + 1000000L, report.hiddenMicros)
    }

    @Test
    fun nothingIsHiddenBelowTheCap() {
        val report = buildUidCpuReport(stats, packages, cap = 100)
        assertEquals(0, report.hiddenRows)
        assertEquals(0L, report.hiddenMicros)
    }

    @Test
    fun totalTimeIsUserPlusSystem() {
        val row = UidCpuRow(10, 3_000_000L, 2_000_000L, emptyList())
        assertEquals(5_000_000L, row.totalMicros)
    }

    @Test
    fun hoursAndMinutesAreShownForLongTimes() {
        assertEquals("1h 0m", UidCpuParsers.formatDuration(3_600_000_000L))
        assertEquals("2h 30m", UidCpuParsers.formatDuration(9_000_000_000L))
    }

    @Test
    fun minutesAndSecondsAreShownForShorterTimes() {
        assertEquals("1m 30s", UidCpuParsers.formatDuration(90_000_000L))
    }

    @Test
    fun secondsAreShownForVeryShortTimes() {
        assertEquals("5s", UidCpuParsers.formatDuration(5_000_000L))
        assertEquals("0s", UidCpuParsers.formatDuration(0L))
        assertEquals("0s", UidCpuParsers.formatDuration(-1L))
    }

    @Test
    fun anEmptyReadingGivesAnEmptyReport() {
        val report = buildUidCpuReport("", "")
        assertTrue(report.rows.isEmpty())
        assertEquals(0L, report.totalMicros)
    }

    @Test
    fun theCommandsOnlyEverRead() {
        assertEquals("cat /proc/uid_cputime/show_uid_stat 2>&1", UidCpuRepository.STATS_COMMAND)
        assertEquals("pm list packages -U", UidCpuRepository.PACKAGES_COMMAND)
        assertEquals("/proc/uid_cputime/show_uid_stat", UID_CPU_PATH)
    }
}
