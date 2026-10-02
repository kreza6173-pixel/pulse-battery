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

    private fun dirOf(pkg: String): String = ShellQuoting.quote("$VAULT_DIR/$pkg")

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
            return ActionResult(false, "$pkg: exit ${done.exit}: " + (done.err + " " + done.out).trim(), pkg)
        }
        val copied = VaultParsers.parseLsFiles(done.out).associateBy { it.name }
        val missing = paths.map { VaultParsers.baseName(it) }
            .filter { (copied[it]?.sizeBytes ?: 0L) <= 0L }
        return if (missing.isEmpty()) {
            val total = copied.values.sumOf { it.sizeBytes }
            ActionResult(true, "$pkg: ${paths.size} APK, $total bytes -> $VAULT_DIR/$pkg", pkg)
        } else {
            ActionResult(false, "$pkg: missing after copy: " + missing.joinToString(", "), pkg)
        }
    }

    fun restore(pkg: String): ActionResult {
        if (!StandbyParsers.isValidPackage(pkg)) return ActionResult(false, "refused: $pkg")
        val dir = dirOf(pkg)
        val command = "rm -rf $STAGING; mkdir -p $STAGING && cp $dir/*.apk $STAGING/ && " +
            "pm install-multiple -r $STAGING/*.apk; r=\$?; rm -rf $STAGING; exit \$r"
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
        private const val STAGING = "/data/local/tmp/pulse_restore"
        private const val APPS_COMMAND = "pm list packages -3"
        private const val LIST_COMMAND =
            "cd $VAULT_DIR 2>/dev/null || exit 0; for d in */; do [ -d \"\$d\" ] || continue; " +
                "echo \"== \${d%/}\"; ls -l \"\$d\"; done"
        private const val TIMEOUT_MS = 15_000
        private const val LONG_TIMEOUT_MS = 120_000
    }
}
