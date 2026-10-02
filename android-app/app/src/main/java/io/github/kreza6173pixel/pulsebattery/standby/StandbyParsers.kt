package io.github.kreza6173pixel.pulsebattery.standby

/** android.app.usage.UsageStatsManager STANDBY_BUCKET_* codes and their `am` names. */
enum class StandbyBucket(val code: Int, val shellName: String, val settable: Boolean) {
    EXEMPTED(5, "exempted", false),
    ACTIVE(10, "active", true),
    WORKING_SET(20, "working_set", true),
    FREQUENT(30, "frequent", true),
    RARE(40, "rare", true),
    RESTRICTED(45, "restricted", true),
    NEVER(50, "never", false);

    companion object {
        fun fromCode(code: Int?): StandbyBucket? = entries.firstOrNull { it.code == code }

        val SETTABLE: List<StandbyBucket> get() = entries.filter { it.settable }
    }
}

/** Pure parsers for M4. Tested against real Xiaomi / Android 16 output where available. */
object StandbyParsers {

    private val PACKAGE_NAME = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    private val BUCKET_LINE = Regex("""^\s*([A-Za-z][A-Za-z0-9_.]*)\s*:\s*(\d+)\s*$""")
    private val STATE = Regex("""^[A-Z_]+$""")

    fun isValidPackage(name: String): Boolean = PACKAGE_NAME.matches(name)

    /** `am get-standby-bucket <pkg>` prints one integer, e.g. `5`. */
    fun parseSingleBucket(output: String): Int? =
        output.trim().lineSequence().firstOrNull()?.trim()?.toIntOrNull()

    /** Lines of the form `com.example: 10`. Anything else is ignored. */
    fun parseBucketList(output: String): Map<String, Int> {
        val map = LinkedHashMap<String, Int>()
        for (line in output.lineSequence()) {
            val m = BUCKET_LINE.matchEntire(line) ?: continue
            val pkg = m.groupValues[1]
            if (isValidPackage(pkg)) map[pkg] = m.groupValues[2].toInt()
        }
        return map
    }

    /** `dumpsys deviceidle whitelist`: lines `type,package,uid`. Returns package to types. */
    fun parseWhitelist(output: String): Map<String, Set<String>> {
        val map = LinkedHashMap<String, MutableSet<String>>()
        for (line in output.lineSequence()) {
            val parts = line.trim().split(',')
            if (parts.size < 2) continue
            val type = parts[0].trim()
            val pkg = parts[1].trim()
            if (type.isEmpty() || !isValidPackage(pkg)) continue
            map.getOrPut(pkg) { LinkedHashSet() }.add(type)
        }
        return map
    }

    /** `pm list packages`: lines `package:com.example`. */
    fun parsePackageList(output: String): List<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { isValidPackage(it) }
            .toList()

    /** `dumpsys deviceidle get deep|light` prints one word such as ACTIVE or IDLE. */
    fun parseIdleState(output: String): String? =
        output.trim().lineSequence().firstOrNull()?.trim()?.takeIf { STATE.matches(it) }
}
