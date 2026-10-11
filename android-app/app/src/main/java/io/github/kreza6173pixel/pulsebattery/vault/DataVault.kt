package io.github.kreza6173pixel.pulsebattery.vault

import io.github.kreza6173pixel.pulsebattery.exec.ShellQuoting

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

    /** init's mount namespace: the one where every app's data directory is mounted. */
    const val INIT_NAMESPACE = "/proc/1/ns/mnt"

    /**
     * Staging area for archives in transit.
     *
     * It has to be on /data rather than on shared storage: init's namespace does not carry
     * the per-user /sdcard view, so a tar written to /sdcard from inside it would not land
     * where the rest of the app looks. /data/local/tmp is the same directory in both
     * namespaces, which makes it the one safe handover point.
     */
    const val STAGING_DIR = "/data/local/tmp/pulse_data"

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

    fun stagedArchive(name: String): String = "$STAGING_DIR/$name"

    /**
     * Re-runs [command] inside init's mount namespace.
     *
     * The whole command is passed as one quoted argument to `sh -c`, so the inner quoting is
     * preserved exactly and nothing in it can be reinterpreted by the outer shell.
     */
    fun inInitNamespace(command: String): String =
        "nsenter --mount=$INIT_NAMESPACE -- sh -c " + ShellQuoting.quote(command)
}

/**
 * How this process can reach another app's data directory.
 *
 * On Android 11 and newer each app process runs in a mount namespace that contains only its
 * own /data/data entry. The privileged user service inherits such a namespace, so being root
 * is not enough: the directory has to be reached through a namespace that has it mounted.
 */
enum class DataAccess {
    /** The directory is visible as-is. */
    DIRECT,

    /** Visible only from init's mount namespace. */
    INIT_NAMESPACE,

    /** Not visible either way. Nothing is attempted. */
    NONE,
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
