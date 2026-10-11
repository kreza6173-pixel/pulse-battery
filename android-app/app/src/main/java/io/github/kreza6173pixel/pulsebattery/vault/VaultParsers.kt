package io.github.kreza6173pixel.pulsebattery.vault

data class VaultFile(val name: String, val sizeBytes: Long)

data class VaultEntry(val pkg: String, val files: List<VaultFile>) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
    val apkCount: Int get() = files.count { it.name.endsWith(".apk") }

    /** Size of the private-data archive, or 0 when there is none. */
    val dataBytes: Long
        get() = files.firstOrNull { it.name == DataVault.DATA_ARCHIVE }?.sizeBytes ?: 0L

    /** Size of the shared-storage archive, or 0 when there is none. */
    val externalBytes: Long
        get() = files.firstOrNull { it.name == DataVault.EXTERNAL_ARCHIVE }?.sizeBytes ?: 0L

    /** A zero-byte archive is a failed backup, not a backup: it does not count. */
    val hasDataArchive: Boolean get() = dataBytes > 0L
}

/** Pure parsers for the vault. Tested against real Xiaomi / Android 16 output. */
object VaultParsers {

    /** toybox `ls -l`: perms links owner group size date time name. */
    private val LS_LINE = Regex("""^-\S+\s+\d+\s+\S+\s+\S+\s+(\d+)\s+\S+\s+\S+\s+(.+)$""")

    /** `pm path <pkg>`: one `package:/data/app/.../x.apk` line per APK (base + splits). */
    fun parsePmPath(output: String): List<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:/") }
            .map { it.removePrefix("package:") }
            .filter { it.endsWith(".apk") && !it.contains('\'') && !it.contains('\n') }
            .toList()

    fun parseLsFiles(output: String): List<VaultFile> =
        output.lineSequence().mapNotNull { line ->
            LS_LINE.matchEntire(line.trim())?.let { m ->
                VaultFile(m.groupValues[2], m.groupValues[1].toLong())
            }
        }.toList()

    /** Output of the listing loop: `== <pkg>` headers each followed by `ls -l` lines. */
    fun parseVaultListing(output: String): List<VaultEntry> {
        val result = mutableListOf<VaultEntry>()
        var pkg: String? = null
        val files = mutableListOf<VaultFile>()

        fun flush() {
            val p = pkg
            if (p != null) result += VaultEntry(p, files.toList())
            files.clear()
        }

        for (line in output.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("== ")) {
                flush()
                pkg = t.removePrefix("== ").trim()
                continue
            }
            LS_LINE.matchEntire(t)?.let { m ->
                files += VaultFile(m.groupValues[2], m.groupValues[1].toLong())
            }
        }
        flush()
        return result
    }

    fun baseName(path: String): String = path.substringAfterLast('/')
}
