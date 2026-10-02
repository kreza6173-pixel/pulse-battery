package io.github.kreza6173pixel.pulsebattery.diag

data class WakeLockEntry(
    /** e.g. PARTIAL_WAKE_LOCK, SCREEN_BRIGHT_WAKE_LOCK. */
    val type: String,
    val tag: String,
    /** e.g. ON_AFTER_RELEASE; empty when none. */
    val flags: String,
    /** Raw age as printed, e.g. "-151ms". */
    val acquired: String,
    val uid: Int?,
    val pid: Int?,
    val pkg: String?,
    /** Package names found in WorkSource{...}: the app the lock is really held for. */
    val workSource: List<String>,
    /** Package named inside a JobScheduler / SyncManager tag (see WakeLockParser). */
    val tagPackage: String? = null,
) {
    /**
     * Who the lock is really for: WorkSource packages, else the package in the tag (system
     * services such as JobScheduler hold locks as pkg=android on behalf of an app), else pkg=.
     */
    val attributedPackages: List<String>
        get() = workSource.ifEmpty { listOfNotNull(tagPackage ?: pkg) }

    val shortType: String get() = type.removeSuffix("_WAKE_LOCK")

    val heldFor: String get() = acquired.removePrefix("-")
}

/**
 * Parses wake-lock lines of `dumpsys power`. Pure; tested against real Android 16 lines.
 * Lines that do not match are ignored, so the whole dump or a grep of it both work.
 *
 * WARNING for editors: never quote a real tag inside a block comment. Tags such as the
 * JobScheduler one start with star-job-star-slash, and a star followed by a slash ENDS a
 * Kotlin comment (CI run after 304564b: WakeLockParser.kt:49:16 Expecting member declaration).
 * Examples live in WakeLockParserTest as string literals instead.
 */
object WakeLockParser {

    private val LINE = Regex("""^\s*([A-Z_]*WAKE_LOCK)\s+'(.*)'\s*(.*?)\s*ACQ=(\S+)(.*)$""")
    private val UID = Regex("""\buid=(\d+)""")
    private val PID = Regex("""\bpid=(\d+)""")
    private val PKG = Regex("""\bpkg=([^\s)]+)""")
    private val WORK_SOURCE = Regex("""WorkSource\{([^}]*)\}""")
    private val SEPARATORS = Regex("""[\s,]+""")
    private val PACKAGE_NAME = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    // Tag form: star, lowercase word, star, optional letter, slash or colon, package name.
    private val TAG_PACKAGE =
        Regex("""^\*[a-z_]+\*[a-z]?[/:]([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)""")

    fun parse(output: String): List<WakeLockEntry> =
        output.lineSequence().mapNotNull { parseLine(it) }.toList()

    fun parseLine(line: String): WakeLockEntry? {
        val m = LINE.matchEntire(line) ?: return null
        val tag = m.groupValues[2]
        val details = m.groupValues[5]
        val ws = WORK_SOURCE.find(details)?.groupValues?.get(1).orEmpty()
        val wsPackages = ws.split(SEPARATORS).filter { PACKAGE_NAME.matches(it) }.distinct()
        return WakeLockEntry(
            type = m.groupValues[1],
            tag = tag,
            flags = m.groupValues[3].trim(),
            acquired = m.groupValues[4],
            uid = UID.find(details)?.groupValues?.get(1)?.toIntOrNull(),
            pid = PID.find(details)?.groupValues?.get(1)?.toIntOrNull(),
            pkg = PKG.find(details)?.groupValues?.get(1),
            workSource = wsPackages,
            tagPackage = TAG_PACKAGE.find(tag)?.groupValues?.get(1),
        )
    }
}
