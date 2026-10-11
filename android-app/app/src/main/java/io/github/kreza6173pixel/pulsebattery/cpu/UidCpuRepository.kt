package io.github.kreza6173pixel.pulsebattery.cpu

import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.exec.ExecOutcome
import io.github.kreza6173pixel.pulsebattery.health.HealthParsers

/**
 * Read-only access to the kernel uid CPU statistics. Every call blocks on the UserService, so
 * call from Dispatchers.IO.
 *
 * The two readings are separate commands on purpose. Together they would be large enough to
 * risk the 64 KiB output cap on a device with several hundred packages, and a truncated
 * reading would silently lose the uid mapping rather than fail.
 */
class UidCpuRepository(private val bridge: ExecBridge) {

    private sealed interface Shell {
        data class Done(val exit: Int, val out: String, val err: String, val truncated: Boolean) : Shell
        data class Failed(val message: String) : Shell
    }

    private fun sh(command: String): Shell =
        when (val outcome = bridge.execBlocking(command, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> Shell.Failed(outcome.message)
            is ExecOutcome.Completed -> Shell.Done(
                outcome.result.exitCode,
                outcome.result.stdout,
                outcome.result.stderr,
                outcome.result.truncated,
            )
        }

    fun read(): DiagResult<UidCpuReport> {
        val stats = when (val result = sh(STATS_COMMAND)) {
            is Shell.Failed -> return DiagResult.Error(result.message, "")
            is Shell.Done -> result
        }
        val statsText = (stats.out + "\n" + stats.err).trim()
        if (stats.truncated) {
            return DiagResult.Error("the statistics output hit the 64 KiB cap", statsText)
        }
        if (!HealthParsers.isReadable(statsText)) {
            return DiagResult.Error("$UID_CPU_PATH could not be read by this uid", statsText)
        }

        val packages = when (val result = sh(PACKAGES_COMMAND)) {
            // The mapping is a convenience: without it the uids are still worth showing.
            is Shell.Failed -> Shell.Done(0, "", "", false)
            is Shell.Done -> result
        }
        val packagesText = if (packages.truncated) "" else packages.out

        val report = buildUidCpuReport(stats.out, packagesText)
        val raw = statsText + "\n\n" + packagesText.trim()
        return if (report.rows.isEmpty()) {
            DiagResult.Error("no uid had any recorded CPU time", raw)
        } else {
            DiagResult.Ok(report, raw)
        }
    }

    companion object {
        /** stderr is folded in so a refused read becomes text the parser can recognise. */
        const val STATS_COMMAND = "cat $UID_CPU_PATH 2>&1"

        const val PACKAGES_COMMAND = "pm list packages -U"

        private const val TIMEOUT_MS = 20_000
    }
}
