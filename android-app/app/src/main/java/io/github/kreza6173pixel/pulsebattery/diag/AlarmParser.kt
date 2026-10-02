package io.github.kreza6173pixel.pulsebattery.diag

/** One row of the "Top Alarms" table of `dumpsys alarm`. */
data class TopAlarm(
    /** Total time spent running, as printed without the leading plus, e.g. "6s376ms". */
    val runningTime: String,
    val wakeups: Int,
    val alarms: Int,
    /** e.g. "u0a145" or "1000". */
    val uid: String,
    val pkg: String,
    /** "walarm" (wakes the device) or "alarm"; null if the tag line was missing. */
    val kind: String?,
    /** Intent action or tag after the colon. */
    val action: String?,
) {
    val isWakeup: Boolean get() = kind == "walarm"
}

/**
 * Parses the Top Alarms block. Pure; tested against real Xiaomi / Android 16 output.
 * Each entry is a stats line followed by a tag line. Indentation is ignored. The block ends
 * at the first blank line or any line that is neither form.
 */
object AlarmParser {

    private const val HEADER = "Top Alarms:"

    private val STATS =
        Regex("""^\s*\+?(\S+) running, (\d+) wakeups, (\d+) alarms: ([^:\s]+):(\S+)\s*$""")

    // star, lowercase word, star, colon, rest.
    private val TAG = Regex("""^\s*\*([a-z_]+)\*:(.*)$""")

    /** Null when the header is absent (unexpected output); empty list when the table is. */
    fun parseTopAlarms(output: String): List<TopAlarm>? {
        val result = mutableListOf<TopAlarm>()
        var inSection = false
        var pending: MatchResult? = null

        fun flush(tag: MatchResult?) {
            val m = pending ?: return
            result += TopAlarm(
                runningTime = m.groupValues[1],
                wakeups = m.groupValues[2].toInt(),
                alarms = m.groupValues[3].toInt(),
                uid = m.groupValues[4],
                pkg = m.groupValues[5],
                kind = tag?.groupValues?.get(1),
                action = tag?.groupValues?.get(2)?.trim(),
            )
            pending = null
        }

        for (line in output.lineSequence()) {
            if (!inSection) {
                if (line.trim() == HEADER) inSection = true
                continue
            }
            if (line.isBlank()) break
            val stats = STATS.matchEntire(line)
            if (stats != null) {
                flush(null)
                pending = stats
                continue
            }
            val tag = TAG.matchEntire(line)
            if (tag != null && pending != null) {
                flush(tag)
                continue
            }
            break
        }
        flush(null)
        return if (inSection) result else null
    }
}
