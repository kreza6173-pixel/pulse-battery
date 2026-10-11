package io.github.kreza6173pixel.pulsebattery.cpu

/**
 * Pure model of the kernel uid CPU statistics. No Android imports, so every rule is covered
 * by unit tests on the JVM.
 *
 * The driver exposes one line per uid with the accumulated user and system time. On the
 * reference phone (Redmi Note 14, tanzanite, mt6789) the node is present while the companion
 * node uid_time_in_state is not, so only this one is read.
 */

/** The node the statistics come from. A constant: never built from input. */
const val UID_CPU_PATH = "/proc/uid_cputime/show_uid_stat"

/** One uid as reported by the driver, with whatever packages share it. */
data class UidCpuRow(
    val uid: Int,
    val userMicros: Long,
    val systemMicros: Long,
    val packages: List<String>,
) {
    val totalMicros: Long get() = userMicros + systemMicros
}

/** The whole reading, already sorted and trimmed for display. */
data class UidCpuReport(
    val rows: List<UidCpuRow>,
    /** Rows left out of [rows] after the display cap. */
    val hiddenRows: Int,
    /** Total time of the rows left out. */
    val hiddenMicros: Long,
    val totalMicros: Long,
)

object UidCpuParsers {

    private val STAT_LINE = Regex("""^\s*(\d+)\s*:\s*(\d+)\s+(\d+)\s*$""")
    private val PACKAGE_LINE = Regex("""^package:(\S+)\s+uid:(\d+)\s*$""")

    /**
     * Parses the driver output. Lines that do not match the documented shape are skipped
     * rather than guessed at, so a different kernel format yields an empty list and the UI
     * can say the node was not understood.
     */
    fun parseStats(output: String): List<Triple<Int, Long, Long>> {
        val rows = ArrayList<Triple<Int, Long, Long>>()
        for (line in output.lineSequence()) {
            val match = STAT_LINE.matchEntire(line.trimEnd()) ?: continue
            val uid = match.groupValues[1].toIntOrNull() ?: continue
            val user = match.groupValues[2].toLongOrNull() ?: continue
            val system = match.groupValues[3].toLongOrNull() ?: continue
            rows.add(Triple(uid, user, system))
        }
        return rows
    }

    /**
     * Parses `pm list packages -U`. Several packages can share a uid, so the value is a list
     * and the order the shell produced is kept.
     */
    fun parseUidPackages(output: String): Map<Int, List<String>> {
        val map = LinkedHashMap<Int, MutableList<String>>()
        for (line in output.lineSequence()) {
            val match = PACKAGE_LINE.matchEntire(line.trim()) ?: continue
            val uid = match.groupValues[2].toIntOrNull() ?: continue
            map.getOrPut(uid) { ArrayList() }.add(match.groupValues[1])
        }
        return map
    }

    /**
     * Microseconds to a short English duration. Pure, so the rounding is tested rather than
     * eyeballed on a screen.
     */
    fun formatDuration(micros: Long): String {
        if (micros <= 0) return "0s"
        val totalSeconds = micros / 1_000_000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0 -> hours.toString() + "h " + minutes + "m"
            minutes > 0 -> minutes.toString() + "m " + seconds + "s"
            else -> seconds.toString() + "s"
        }
    }
}

/** Largest number of rows shown. Beyond this the tail is summarised instead. */
const val UID_CPU_DISPLAY_CAP = 40

/**
 * Joins the driver output with the uid mapping and sorts by total time.
 *
 * Uids with no recorded time are dropped: the driver lists every uid it has ever seen, and a
 * page of zeroes buries the handful that matter.
 */
fun buildUidCpuReport(
    statsOutput: String,
    packagesOutput: String,
    cap: Int = UID_CPU_DISPLAY_CAP,
): UidCpuReport {
    val names = UidCpuParsers.parseUidPackages(packagesOutput)
    val all = UidCpuParsers.parseStats(statsOutput)
        .map { (uid, user, system) -> UidCpuRow(uid, user, system, names[uid].orEmpty()) }
        .filter { it.totalMicros > 0 }
        .sortedWith(compareByDescending<UidCpuRow> { it.totalMicros }.thenBy { it.uid })
    val shown = all.take(cap)
    val hidden = all.drop(cap)
    return UidCpuReport(
        rows = shown,
        hiddenRows = hidden.size,
        hiddenMicros = hidden.sumOf { it.totalMicros },
        totalMicros = all.sumOf { it.totalMicros },
    )
}
