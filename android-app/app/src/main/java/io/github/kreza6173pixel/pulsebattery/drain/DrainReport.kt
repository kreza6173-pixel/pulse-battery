package io.github.kreza6173pixel.pulsebattery.drain

import java.util.Locale

/** Snapshot taken when a period starts. uptimeMs is SystemClock.elapsedRealtime(). */
data class Baseline(
    val timeMs: Long,
    val uptimeMs: Long,
    val wakeups: Map<String, Int>,
)

data class Offender(
    val pkg: String,
    val wakeups: Int,
    /** Null when the period is too short (or unknown) for a meaningful rate. */
    val perHour: Double?,
)

data class DrainReportResult(
    /** Null when no period was started: counts are then since the system began counting. */
    val elapsedMs: Long?,
    /** The device restarted during the period, so counts start at that restart. */
    val rebooted: Boolean,
    val total: Int,
    val offenders: List<Offender>,
)

/** Pure logic of the drain report. No Android types, unit-tested. */
object DrainReport {

    private const val HOUR_MS = 3_600_000.0

    /** Rates over shorter periods are noise, so none is shown. */
    const val MIN_RATE_PERIOD_MS = 10 * 60_000L

    /** First line `timeMs uptimeMs`, then one `pkg count` line per package. */
    fun encode(b: Baseline): String = buildString {
        append(b.timeMs).append(' ').append(b.uptimeMs)
        for ((pkg, n) in b.wakeups) {
            append('\n').append(pkg).append(' ').append(n)
        }
    }

    fun decode(text: String?): Baseline? {
        if (text.isNullOrBlank()) return null
        val lines = text.lines()
        val head = lines.first().trim().split(' ')
        if (head.size != 2) return null
        val time = head[0].toLongOrNull() ?: return null
        val uptime = head[1].toLongOrNull() ?: return null
        val map = LinkedHashMap<String, Int>()
        for (line in lines.drop(1)) {
            val parts = line.trim().split(' ')
            if (parts.size != 2 || parts[0].isEmpty()) continue
            val n = parts[1].toIntOrNull() ?: continue
            map[parts[0]] = n
        }
        return Baseline(time, uptime, map)
    }

    fun compute(
        baseline: Baseline?,
        now: Map<String, Int>,
        nowMs: Long,
        nowUptimeMs: Long,
    ): DrainReportResult {
        val rebooted = baseline != null && nowUptimeMs < baseline.uptimeMs
        val base: Map<String, Int> = if (baseline == null || rebooted) emptyMap() else baseline.wakeups
        val elapsed: Long? = if (baseline == null) null else (nowMs - baseline.timeMs).coerceAtLeast(0L)
        val hours: Double? = if (elapsed != null && elapsed >= MIN_RATE_PERIOD_MS) elapsed / HOUR_MS else null
        val offenders = now.mapNotNull { (pkg, cur) ->
            val before = base[pkg] ?: 0
            // A counter that went down was reset: everything it shows now is new.
            val delta = if (cur >= before) cur - before else cur
            if (delta <= 0) null else Offender(pkg, delta, hours?.let { delta / it })
        }.sortedWith(compareByDescending<Offender> { it.wakeups }.thenBy { it.pkg })
        return DrainReportResult(elapsed, rebooted, offenders.sumOf { it.wakeups }, offenders)
    }

    /** "2h 5m", "45m", or "<1m" for the first minute so a fresh period never reads as 0. */
    fun formatDuration(ms: Long): String {
        val totalMin = ms.coerceAtLeast(0L) / 60_000L
        val h = totalMin / 60L
        val m = totalMin % 60L
        return when {
            h > 0L -> "${h}h ${m}m"
            m > 0L -> "${m}m"
            else -> "<1m"
        }
    }

    fun formatRate(perHour: Double): String = String.format(Locale.US, "%.1f", perHour)

    /** Plain-text version for Copy / Share. */
    fun toText(r: DrainReportResult, limit: Int): String = buildString {
        append("PULSE // BATTERY drain report\n")
        val e = r.elapsedMs
        if (e == null) append("Period: since boot\n") else append("Period: ").append(formatDuration(e)).append('\n')
        if (r.rebooted) append("Device restarted during the period\n")
        append("Total alarm wakeups: ").append(r.total).append('\n')
        for (o in r.offenders.take(limit)) {
            append(o.pkg).append("  ").append(o.wakeups)
            val rate = o.perHour
            if (rate != null) append("  (").append(formatRate(rate)).append("/h)")
            append('\n')
        }
    }
}
