package io.github.kreza6173pixel.pulsebattery.vault

import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.exec.ExecOutcome
import io.github.kreza6173pixel.pulsebattery.exec.ShellQuoting
import io.github.kreza6173pixel.pulsebattery.standby.ActionResult
import io.github.kreza6173pixel.pulsebattery.standby.StandbyParsers

data class VaultSnapshot(val apps: List<String>, val vault: List<VaultEntry>)

/**
 * M5 Backup Vault. Every call blocks on the UserService: call from Dispatchers.IO.
 *
 * Facts from the phone: shell can copy APKs out of /data/app into shared storage, but
 * system_server cannot read shared storage (fuse context), so installs are staged in
 * /data/local/tmp, which is what the pm error message itself recommends.
 * `install-multiple` is an adb client command and does not exist on the device
 * ("Unknown command: install-multiple"); device-side `pm install` accepts base + splits.
 *
 * The APK paths work with the shell uid. The data archive needs root AND a mount namespace
 * that can see the directory: see [DataAccess].
 */
class VaultRepository(private val bridge: ExecBridge) {

    private sealed interface Shell {
        data class Done(val exit: Int, val out: String, val err: String) : Shell
        data class Failed(val message: String) : Shell
    }

    private fun sh(command: String, timeoutMs: Int = TIMEOUT_MS): Shell =
        when (val o = bridge.execBlocking(command, timeoutMs)) {
            is ExecOutcome.Failed -> Shell.Failed(o.message)
            is ExecOutcome.Completed -> Shell.Done(o.result.exitCode, o.result.stdout, o.result.stderr)
        }

    /** Runs a command in whichever namespace can actually see the app data directory. */
    private fun shIn(access: DataAccess, command: String, timeoutMs: Int = TIMEOUT_MS): Shell =
        when (access) {
            DataAccess.DIRECT -> sh(command, timeoutMs)
            DataAccess.INIT_NAMESPACE -> sh(DataVault.inInitNamespace(command), timeoutMs)
            DataAccess.NONE -> Shell.Failed(REFUSE_NO_DATA_VIEW)
        }

    private fun dirOf(pkg: String): String = ShellQuoting.quote("$VAULT_DIR/$pkg")

    /** Combined output of one step, for the result card. */
    private fun Shell.Done.text(): String = (out + " " + err).trim()

    private fun probedYes(result: Shell): Boolean = when (result) {
        is Shell.Failed -> false
        is Shell.Done -> DataVaultParsers.probedYes(result.out)
    }

    private fun dirExists(path: String): Boolean =
        probedYes(sh("[ -d " + ShellQuoting.quote(path) + " ] && echo yes"))

    /**
     * Decides how, if at all, the app data directory can be reached.
     *
     * Cheap: two directory probes. Done per operation rather than cached, because a Shizuku
     * or Sui restart can land the service in a different namespace.
     */
    private fun dataAccess(pkg: String): DataAccess {
        val probe = "[ -d " + ShellQuoting.quote(DataVault.privateDir(pkg)) + " ] && echo yes"
        if (probedYes(sh(probe))) return DataAccess.DIRECT
        if (probedYes(sh(DataVault.inInitNamespace(probe)))) return DataAccess.INIT_NAMESPACE
        return DataAccess.NONE
    }

    private fun clearStaging() {
        sh("rm -rf " + ShellQuoting.quote(DataVault.STAGING_DIR))
    }

    fun load(): DiagResult<VaultSnapshot> {
        val apps = when (val r = sh(APPS_COMMAND)) {
            is Shell.Failed -> return DiagResult.Error(r.message, "")
            is Shell.Done -> StandbyParsers.parsePackageList(r.out).sorted()
        }
        val listing = when (val r = sh(LIST_COMMAND)) {
            is Shell.Failed -> return DiagResult.Error(r.message, "")
            is Shell.Done -> r
        }
        val vault = VaultParsers.parseVaultListing(listing.out)
            .filter { StandbyParsers.isValidPackage(it.pkg) }
        return DiagResult.Ok(VaultSnapshot(apps, vault), listing.out + listing.err)
    }

    fun export(pkg: String): ActionResult {
        if (!StandbyParsers.isValidPackage(pkg)) return ActionResult(false, "refused: $pkg")
        val paths = when (val r = sh("pm path " + ShellQuoting.quote(pkg))) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> VaultParsers.parsePmPath(r.out)
        }
        if (paths.isEmpty()) return ActionResult(false, "$pkg: pm path returned no APK", pkg)

        val dir = dirOf(pkg)
        val sources = paths.joinToString(" ") { ShellQuoting.quote(it) }
        val command = "mkdir -p $dir && rm -f $dir/*.apk && cp $sources $dir/ && ls -l $dir"
        val done = when (val r = sh(command, LONG_TIMEOUT_MS)) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> r
        }
        if (done.exit != 0) {
            return ActionResult(false, "$pkg: exit ${done.exit}: " + done.text(), pkg)
        }
        val copied = VaultParsers.parseLsFiles(done.out).associateBy { it.name }
        val missing = paths.map { VaultParsers.baseName(it) }
            .filter { (copied[it]?.sizeBytes ?: 0L) <= 0L }
        return if (missing.isEmpty()) {
            val total = copied.values.filter { it.name.endsWith(".apk") }.sumOf { it.sizeBytes }
            ActionResult(true, "$pkg: ${paths.size} APK, $total bytes -> $VAULT_DIR/$pkg", pkg)
        } else {
            ActionResult(false, "$pkg: missing after copy: " + missing.joinToString(", "), pkg)
        }
    }

    /**
     * Archives the whole private data directory, plus the shared-storage data and obb
     * directories when they exist. Root only, and the archive is staged on /data before it
     * reaches the vault: see [DataVault.STAGING_DIR] for why.
     */
    fun exportData(pkg: String, rootAvailable: Boolean): ActionResult {
        if (!StandbyParsers.isValidPackage(pkg)) {
            return ActionResult(false, "refused: invalid package name")
        }
        if (!rootAvailable) return ActionResult(false, REFUSE_NO_ROOT, pkg)
        val quoted = ShellQuoting.quote(pkg)
        val dir = dirOf(pkg)
        val log = StringBuilder()

        val installed = when (val r = sh("pm path $quoted")) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> VaultParsers.parsePmPath(r.out).isNotEmpty()
        }
        if (!installed) return ActionResult(false, "$pkg: not installed, nothing to back up", pkg)

        val access = dataAccess(pkg)
        if (access == DataAccess.NONE) {
            return ActionResult(false, "$pkg: $REFUSE_NO_DATA_VIEW", pkg)
        }
        log.append("data directory reached: ").append(access.name).append('\n')

        // Quiesce first: a database caught mid-write is archived as a corrupt database.
        sh("am force-stop $quoted")

        clearStaging()
        val staging = ShellQuoting.quote(DataVault.STAGING_DIR)
        val stagedData = ShellQuoting.quote(DataVault.stagedArchive(DataVault.DATA_ARCHIVE))
        when (val r = sh("mkdir -p $staging")) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> if (r.exit != 0) {
                return ActionResult(false, "$pkg: staging mkdir exit ${r.exit}: " + r.text(), pkg)
            }
        }

        val privateDir = ShellQuoting.quote(DataVault.privateDir(pkg))
        when (val r = shIn(access, "tar -czf $stagedData -C $privateDir .", DATA_TIMEOUT_MS)) {
            is Shell.Failed -> {
                clearStaging()
                return ActionResult(false, r.message, pkg)
            }
            is Shell.Done -> {
                log.append("tar exit ${r.exit}")
                if (r.text().isNotEmpty()) log.append(": ").append(r.text())
                log.append('\n')
            }
        }

        // An archive is only a backup if it has a size AND can be listed.
        val archivedBytes = when (val r = sh("ls -l $stagedData")) {
            is Shell.Failed -> 0L
            is Shell.Done -> VaultParsers.parseLsFiles(r.out).firstOrNull()?.sizeBytes ?: 0L
        }
        if (archivedBytes <= 0L) {
            clearStaging()
            return ActionResult(false, "$pkg: the data archive is empty.\n$log", pkg)
        }
        val entries = when (val r = sh("tar -tzf $stagedData | head -n 5", LONG_TIMEOUT_MS)) {
            is Shell.Failed -> emptyList()
            is Shell.Done -> DataVaultParsers.parseArchiveEntries(r.out)
        }
        if (entries.isEmpty()) {
            clearStaging()
            return ActionResult(false, "$pkg: the data archive cannot be listed.\n$log", pkg)
        }

        // Handover to shared storage happens in the ordinary namespace, which is the only one
        // with the per-user /sdcard view.
        val vaultData = "$dir/${DataVault.DATA_ARCHIVE}"
        when (val r = sh("mkdir -p $dir && cp $stagedData $vaultData", LONG_TIMEOUT_MS)) {
            is Shell.Failed -> {
                clearStaging()
                return ActionResult(false, r.message, pkg)
            }
            is Shell.Done -> if (r.exit != 0) {
                clearStaging()
                return ActionResult(false, "$pkg: copy to vault exit ${r.exit}: " + r.text(), pkg)
            }
        }
        clearStaging()

        val vaultBytes = when (val r = sh("ls -l $vaultData")) {
            is Shell.Failed -> 0L
            is Shell.Done -> VaultParsers.parseLsFiles(r.out).firstOrNull()?.sizeBytes ?: 0L
        }
        if (vaultBytes != archivedBytes) {
            return ActionResult(
                false,
                "$pkg: archive is $archivedBytes bytes but $vaultBytes landed in the vault.\n$log",
                pkg,
            )
        }
        log.append("${DataVault.DATA_ARCHIVE}: $vaultBytes bytes, listable\n")

        // External members are optional: a missing one must not fail the whole backup.
        val members = buildList {
            if (dirExists(DataVault.externalDir(pkg))) add(DataVault.externalMember(pkg))
            if (dirExists(DataVault.obbDir(pkg))) add(DataVault.obbMember(pkg))
        }
        if (members.isEmpty()) {
            log.append("no external data or obb directory\n")
        } else {
            val externalArchive = "$dir/${DataVault.EXTERNAL_ARCHIVE}"
            val root = ShellQuoting.quote(DataVault.EXTERNAL_ROOT)
            val list = members.joinToString(" ") { ShellQuoting.quote(it) }
            when (val r = sh("tar -czf $externalArchive -C $root $list", DATA_TIMEOUT_MS)) {
                is Shell.Failed -> log.append("external: ").append(r.message).append('\n')
                is Shell.Done -> log.append("external tar exit ${r.exit}\n")
            }
            val externalBytes = when (val r = sh("ls -l $externalArchive")) {
                is Shell.Failed -> 0L
                is Shell.Done -> VaultParsers.parseLsFiles(r.out).firstOrNull()?.sizeBytes ?: 0L
            }
            log.append("${DataVault.EXTERNAL_ARCHIVE}: $externalBytes bytes\n")
        }

        return ActionResult(true, "$pkg -> $VAULT_DIR/$pkg\n$log".trim(), pkg)
    }

    /**
     * Puts an archived data directory back.
     *
     * The app must be installed: its data belongs to a uid that only exists while it is, and
     * the live uid read off the data directory is what ownership is restored to. SELinux
     * labels are restored last; when that step cannot run, the result is reported as not
     * applied, because an app with wrong labels crashes on launch.
     */
    fun restoreData(pkg: String, rootAvailable: Boolean): ActionResult {
        if (!StandbyParsers.isValidPackage(pkg)) {
            return ActionResult(false, "refused: invalid package name")
        }
        if (!rootAvailable) return ActionResult(false, REFUSE_NO_ROOT, pkg)
        val quoted = ShellQuoting.quote(pkg)
        val dir = dirOf(pkg)
        val vaultData = "$dir/${DataVault.DATA_ARCHIVE}"
        val privateDir = ShellQuoting.quote(DataVault.privateDir(pkg))
        val log = StringBuilder()

        val archivedBytes = when (val r = sh("ls -l $vaultData")) {
            is Shell.Failed -> 0L
            is Shell.Done -> VaultParsers.parseLsFiles(r.out).firstOrNull()?.sizeBytes ?: 0L
        }
        if (archivedBytes <= 0L) {
            return ActionResult(false, "$pkg: no usable data archive in the vault", pkg)
        }

        val installed = when (val r = sh("pm path $quoted")) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> VaultParsers.parsePmPath(r.out).isNotEmpty()
        }
        if (!installed) return ActionResult(false, "$pkg: $REFUSE_NOT_INSTALLED", pkg)

        val access = dataAccess(pkg)
        if (access == DataAccess.NONE) {
            return ActionResult(false, "$pkg: $REFUSE_NO_DATA_VIEW", pkg)
        }
        log.append("data directory reached: ").append(access.name).append('\n')

        sh("am force-stop $quoted")

        // Stage on /data first: init's namespace cannot see the per-user /sdcard view.
        clearStaging()
        val staging = ShellQuoting.quote(DataVault.STAGING_DIR)
        val stagedData = ShellQuoting.quote(DataVault.stagedArchive(DataVault.DATA_ARCHIVE))
        when (val r = sh("mkdir -p $staging && cp $vaultData $stagedData", LONG_TIMEOUT_MS)) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> if (r.exit != 0) {
                clearStaging()
                return ActionResult(false, "$pkg: staging copy exit ${r.exit}: " + r.text(), pkg)
            }
        }

        val uid = when (val r = shIn(access, "stat -c %u $privateDir")) {
            is Shell.Failed -> null
            is Shell.Done -> DataVaultParsers.parseUid(r.out)
        }
        if (uid == null) {
            clearStaging()
            return ActionResult(
                false,
                "$pkg: could not read the owner uid of the data directory",
                pkg,
            )
        }
        log.append("owner uid before: $uid\n")

        val extractOk = when (
            val r = shIn(access, "tar -xzf $stagedData -C $privateDir", DATA_TIMEOUT_MS)
        ) {
            is Shell.Failed -> {
                log.append("extract failed: ").append(r.message).append('\n')
                false
            }
            is Shell.Done -> {
                log.append("extract exit ${r.exit}")
                if (r.text().isNotEmpty()) log.append(": ").append(r.text())
                log.append('\n')
                r.exit == 0
            }
        }

        val chownOk = when (val r = shIn(access, "chown -R $uid:$uid $privateDir", LONG_TIMEOUT_MS)) {
            is Shell.Failed -> false
            is Shell.Done -> {
                log.append("chown exit ${r.exit}\n")
                r.exit == 0
            }
        }

        // Without the right SELinux labels the app cannot read its own files.
        val relabelOk = when (val r = shIn(access, "restorecon -RF $privateDir", LONG_TIMEOUT_MS)) {
            is Shell.Failed -> false
            is Shell.Done -> {
                log.append("restorecon exit ${r.exit}")
                if (r.text().isNotEmpty()) log.append(": ").append(r.text())
                log.append('\n')
                r.exit == 0
            }
        }
        if (!relabelOk) log.append(RELABEL_WARNING).append('\n')

        clearStaging()

        // Shared storage is restored in the ordinary namespace, which is the one that has it.
        val externalArchive = "$dir/${DataVault.EXTERNAL_ARCHIVE}"
        val externalBytes = when (val r = sh("ls -l $externalArchive")) {
            is Shell.Failed -> 0L
            is Shell.Done -> VaultParsers.parseLsFiles(r.out).firstOrNull()?.sizeBytes ?: 0L
        }
        if (externalBytes > 0L) {
            val root = ShellQuoting.quote(DataVault.EXTERNAL_ROOT)
            when (val r = sh("tar -xzf $externalArchive -C $root", DATA_TIMEOUT_MS)) {
                is Shell.Failed -> log.append("external: ").append(r.message).append('\n')
                is Shell.Done -> log.append("external extract exit ${r.exit}\n")
            }
        }

        sh("am force-stop $quoted")

        val count = when (val r = shIn(access, "ls -a $privateDir | wc -l")) {
            is Shell.Failed -> null
            is Shell.Done -> DataVaultParsers.parseCount(r.out)
        }
        val uidAfter = when (val r = shIn(access, "stat -c %u $privateDir")) {
            is Shell.Failed -> null
            is Shell.Done -> DataVaultParsers.parseUid(r.out)
        }
        log.append("entries after: ${count ?: "unknown"}, owner uid after: ${uidAfter ?: "unknown"}\n")

        val ok = extractOk && chownOk && relabelOk &&
            (count ?: 0) > EMPTY_DIR_ENTRIES && uidAfter == uid
        return ActionResult(ok, "$pkg\n$log".trim(), pkg)
    }

    /** Removes only the data archives, leaving any saved APKs in place. */
    fun deleteData(pkg: String): ActionResult {
        if (!StandbyParsers.isValidPackage(pkg)) {
            return ActionResult(false, "refused: invalid package name")
        }
        val dir = dirOf(pkg)
        when (val r = sh("rm -f $dir/${DataVault.DATA_ARCHIVE} $dir/${DataVault.EXTERNAL_ARCHIVE}")) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> Unit
        }
        val left = when (val r = sh("ls -l $dir")) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> VaultParsers.parseLsFiles(r.out).map { it.name }
        }
        val stillThere = left.any {
            it == DataVault.DATA_ARCHIVE || it == DataVault.EXTERNAL_ARCHIVE
        }
        return if (!stillThere) {
            ActionResult(true, "$pkg: data archives removed", pkg)
        } else {
            ActionResult(false, "$pkg: archives still present after rm", pkg)
        }
    }

    fun restore(pkg: String): ActionResult {
        if (!StandbyParsers.isValidPackage(pkg)) return ActionResult(false, "refused: $pkg")
        val dir = dirOf(pkg)
        val command = "rm -rf $STAGING; mkdir -p $STAGING && cp $dir/*.apk $STAGING/ && " +
            "pm install -r $STAGING/*.apk; r=\$?; rm -rf $STAGING; exit \$r"
        val done = when (val r = sh(command, LONG_TIMEOUT_MS)) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> r
        }
        val printed = (done.out + "\n" + done.err).trim()
        val ok = done.exit == 0 && printed.contains("Success")
        return ActionResult(ok, "$pkg: exit ${done.exit}\n$printed", pkg)
    }

    fun delete(pkg: String): ActionResult {
        if (!StandbyParsers.isValidPackage(pkg)) return ActionResult(false, "refused: $pkg")
        when (val r = sh("rm -rf " + dirOf(pkg))) {
            is Shell.Failed -> return ActionResult(false, r.message, pkg)
            is Shell.Done -> Unit
        }
        val stillThere = when (val r = sh(LIST_COMMAND)) {
            is Shell.Failed -> null
            is Shell.Done -> VaultParsers.parseVaultListing(r.out).any { it.pkg == pkg }
        }
        return if (stillThere == false) {
            ActionResult(true, "$pkg: removed from vault", pkg)
        } else {
            ActionResult(false, "$pkg: still present after rm (read-back = $stillThere)", pkg)
        }
    }

    companion object {
        const val VAULT_DIR = "/sdcard/Download/PulseVault"

        const val REFUSE_NO_ROOT =
            "refused: the data archive needs the privileged service to run as root (uid 0). " +
                "/data/data is unreadable for the shell uid."

        const val REFUSE_NOT_INSTALLED =
            "not installed. Restore the APK first: app data belongs to a uid that only exists " +
                "while the app is installed."

        const val REFUSE_NO_DATA_VIEW =
            "the data directory is not visible from this process, not even through init's " +
                "mount namespace. Android 11 and newer isolate app data per mount namespace, " +
                "and nsenter is what gets past it. Nothing was changed."

        const val RELABEL_WARNING =
            "SELinux labels were not restored, so the app will probably crash on launch. " +
                "Reported as NOT applied even though the files were written."

        /** `ls -a` of an empty directory still prints `.` and `..`. */
        private const val EMPTY_DIR_ENTRIES = 2

        private const val STAGING = "/data/local/tmp/pulse_restore"
        private const val APPS_COMMAND = "pm list packages -3"
        private const val LIST_COMMAND =
            "cd $VAULT_DIR 2>/dev/null || exit 0; for d in */; do [ -d \"\$d\" ] || continue; " +
                "echo \"== \${d%/}\"; ls -l \"\$d\"; done"
        private const val TIMEOUT_MS = 15_000
        private const val LONG_TIMEOUT_MS = 120_000

        /** Archiving gigabytes of data is slow; the UI shows a working state meanwhile. */
        private const val DATA_TIMEOUT_MS = 600_000
    }
}
