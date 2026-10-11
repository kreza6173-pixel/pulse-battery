package io.github.kreza6173pixel.pulsebattery.appops

/**
 * The app-op modes `cmd appops` accepts and prints.
 *
 * DEFAULT is special: an op the app has never exercised is not printed at all, so an absent
 * entry and the literal word `default` mean the same thing. [AppOpsParsers.modeOf] folds both
 * into [DEFAULT] and the read-back relies on that.
 */
enum class AppOpMode(val shellName: String) {
    ALLOW("allow"),
    IGNORE("ignore"),
    DENY("deny"),
    DEFAULT("default"),
    FOREGROUND("foreground");

    companion object {
        fun fromShell(name: String?): AppOpMode? {
            val n = name?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.shellName == n }
        }

        /** What the UI offers. deny and foreground are left out: not useful for these ops. */
        val OFFERED: List<AppOpMode> get() = listOf(ALLOW, IGNORE, DEFAULT)
    }
}

/**
 * The three battery-relevant app ops, fixed at compile time.
 *
 * The op name is never taken from user input: the shell only ever sees one of these constants,
 * so no quoting question arises for the op argument.
 */
enum class BatteryAppOp(val opName: String) {
    /** PowerManager wakelocks. ignore makes every acquire a silent no-op for that app. */
    WAKE_LOCK("WAKE_LOCK"),

    /** Background services. ignore is what the Settings background restriction uses. */
    RUN_IN_BACKGROUND("RUN_IN_BACKGROUND"),

    /** The wider "any background work" op introduced with the background limits. */
    RUN_ANY_IN_BACKGROUND("RUN_ANY_IN_BACKGROUND"),
}

/** Pure parsers. No Android, no shell: unit-tested on the JVM. */
object AppOpsParsers {

    private val PACKAGE_NAME = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    /** e.g. `WAKE_LOCK: ignore; time=+1h2m3s456ms ago; duration=+1m0s0ms`. */
    private val OP_LINE = Regex("""^\s*([A-Z][A-Z0-9_]*)\s*:\s*([A-Za-z]+)\b.*$""")

    const val NO_OPERATIONS = "No operations."

    fun isValidPackage(name: String): Boolean = PACKAGE_NAME.matches(name)

    /**
     * Reads every `OP: mode` line. Lines the shell adds for nested entries (uid blocks,
     * attribution tags) do not match the shape and are ignored.
     */
    fun parseModes(output: String): Map<String, AppOpMode> {
        val map = LinkedHashMap<String, AppOpMode>()
        for (line in output.lineSequence()) {
            val m = OP_LINE.matchEntire(line) ?: continue
            val mode = AppOpMode.fromShell(m.groupValues[2]) ?: continue
            map.putIfAbsent(m.groupValues[1], mode)
        }
        return map
    }

    /** An op that is absent from the output is at its default mode, not unknown. */
    fun modeOf(modes: Map<String, AppOpMode>, op: BatteryAppOp): AppOpMode =
        modes[op.opName] ?: AppOpMode.DEFAULT

    /** True when the output says the package has no recorded ops at all. */
    fun isNoOperations(output: String): Boolean = output.trim() == NO_OPERATIONS

    /** `pm list packages`: lines `package:com.example`. */
    fun parsePackageList(output: String): List<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { isValidPackage(it) }
            .toList()
}
