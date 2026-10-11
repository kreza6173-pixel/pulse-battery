package io.github.kreza6173pixel.pulsebattery.vault

/**
 * Paths and pure helpers for the full-data backup.
 *
 * Nothing here touches the device: the repository builds its commands from these constants so
 * the layout is stated in one place and can be asserted in tests.
 */
object DataVault {

    /** Archive of the private data directory. */
    const val DATA_ARCHIVE = "data.tar.gz"

    /** Archive of the shared-storage data and obb directories, when they exist. */
    const val EXTERNAL_ARCHIVE = "external.tar.gz"

    /** Parent of the external members, so one archive can hold both of them. */
    const val EXTERNAL_ROOT = "/sdcard/Android"

    /**
     * The app's private data. /data/data is the legacy symlink to /data/user/0 and is what
     * every shell on the device resolves, so it is used directly rather than guessing a user id.
     */
    fun privateDir(pkg: String): String = "/data/data/$pkg"

    /** Member paths inside [EXTERNAL_ROOT], relative on purpose so tar stores them relative. */
    fun externalMember(pkg: String): String = "data/$pkg"

    fun obbMember(pkg: String): String = "obb/$pkg"

    fun externalDir(pkg: String): String = "$EXTERNAL_ROOT/${externalMember(pkg)}"

    fun obbDir(pkg: String): String = "$EXTERNAL_ROOT/${obbMember(pkg)}"
}

/** Pure parsers for the data backup. Unit-tested on the JVM. */
object DataVaultParsers {

    private val UID_LINE = Regex("""^\d+$""")

    /** `stat -c %u <dir>` prints one integer. An error line must not parse as a uid. */
    fun parseUid(output: String): Int? =
        output.trim().lineSequence().firstOrNull()?.trim()
            ?.takeIf { UID_LINE.matches(it) }
            ?.toIntOrNull()

    /** `wc -l` prints a possibly space-padded integer. */
    fun parseCount(output: String): Int? =
        output.trim().lineSequence().firstOrNull()?.trim()?.toIntOrNull()

    /**
     * Entries printed by `tar -tzf`. Lines tar writes to report a problem start with `tar:`
     * and are not entries, so an unreadable archive yields an empty list.
     */
    fun parseArchiveEntries(output: String): List<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("tar:") }
            .toList()

    /** True when a `[ -d path ] && echo yes` probe said yes. */
    fun probedYes(output: String): Boolean =
        output.lineSequence().any { it.trim() == "yes" }

    fun megabytes(bytes: Long): Double = bytes / (1024.0 * 1024.0)
}
