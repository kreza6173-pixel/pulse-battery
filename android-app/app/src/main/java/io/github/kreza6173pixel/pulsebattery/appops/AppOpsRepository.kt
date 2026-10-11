package io.github.kreza6173pixel.pulsebattery.appops

import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.exec.ExecOutcome
import io.github.kreza6173pixel.pulsebattery.exec.ShellQuoting

data class AppOpsSnapshot(val pkg: String, val modes: Map<String, AppOpMode>) {
    fun modeOf(op: BatteryAppOp): AppOpMode = AppOpsParsers.modeOf(modes, op)
}

/** Outcome of one op write. [ok] is true only when a read-back confirmed the new mode. */
data class OpResult(val ok: Boolean, val message: String)

/**
 * `cmd appops` wrapper.
 *
 * These ops are settable with the shell uid, so this works in shell mode as well as root mode:
 * no root gate here. Every call blocks on the UserService, so call it from Dispatchers.IO.
 */
class AppOpsRepository(private val bridge: ExecBridge) {

    private sealed interface Shell {
        data class Done(val exit: Int, val out: String, val err: String) : Shell
        data class Failed(val message: String) : Shell
    }

    private fun sh(command: String, timeoutMs: Int = TIMEOUT_MS): Shell =
        when (val o = bridge.execBlocking(command, timeoutMs)) {
            is ExecOutcome.Failed -> Shell.Failed(o.message)
            is ExecOutcome.Completed -> Shell.Done(o.result.exitCode, o.result.stdout, o.result.stderr)
        }

    fun packages(): DiagResult<List<String>> = when (val r = sh(PACKAGES_COMMAND)) {
        is Shell.Failed -> DiagResult.Error(r.message, "")
        is Shell.Done -> {
            val list = AppOpsParsers.parsePackageList(r.out)
            if (list.isEmpty()) {
                DiagResult.Error("no packages in output", r.out + r.err)
            } else {
                DiagResult.Ok(list.sorted(), r.out)
            }
        }
    }

    fun load(pkg: String): DiagResult<AppOpsSnapshot> {
        if (!AppOpsParsers.isValidPackage(pkg)) return DiagResult.Error("invalid package name", "")
        return when (val r = sh("cmd appops get " + ShellQuoting.quote(pkg))) {
            is Shell.Failed -> DiagResult.Error(r.message, "")
            is Shell.Done -> {
                val raw = (r.out + r.err)
                if (r.exit != 0) {
                    DiagResult.Error("exit ${r.exit}", raw)
                } else {
                    // "No operations." is a valid answer: everything is at its default.
                    DiagResult.Ok(AppOpsSnapshot(pkg, AppOpsParsers.parseModes(r.out)), raw)
                }
            }
        }
    }

    fun setMode(pkg: String, op: BatteryAppOp, mode: AppOpMode): OpResult {
        if (!AppOpsParsers.isValidPackage(pkg)) return OpResult(false, "refused: invalid package name")
        val before = readMode(pkg, op)
        val command = "cmd appops set " + ShellQuoting.quote(pkg) + " " + op.opName + " " + mode.shellName
        when (val r = sh(command)) {
            is Shell.Failed -> return OpResult(false, r.message)
            is Shell.Done -> if (r.exit != 0) {
                return OpResult(false, "exit ${r.exit}: " + (r.err + " " + r.out).trim())
            }
        }
        val after = readMode(pkg, op)
        val from = before?.shellName ?: "unknown"
        return if (after == mode) {
            OpResult(true, "$pkg ${op.opName}: $from -> ${mode.shellName}")
        } else {
            OpResult(
                false,
                "$pkg ${op.opName}: asked ${mode.shellName}, system reports " +
                    (after?.shellName ?: "unknown"),
            )
        }
    }

    /** Puts all three ops back to default, one write at a time, each read back. */
    fun resetAll(pkg: String): OpResult {
        val lines = BatteryAppOp.entries.map { setMode(pkg, it, AppOpMode.DEFAULT) }
        return OpResult(lines.all { it.ok }, lines.joinToString("\n") { it.message })
    }

    private fun readMode(pkg: String, op: BatteryAppOp): AppOpMode? =
        when (val r = sh("cmd appops get " + ShellQuoting.quote(pkg) + " " + op.opName)) {
            is Shell.Failed -> null
            is Shell.Done ->
                if (r.exit != 0) null else AppOpsParsers.modeOf(AppOpsParsers.parseModes(r.out), op)
        }

    private companion object {
        const val PACKAGES_COMMAND = "pm list packages -3"
        const val TIMEOUT_MS = 15_000
    }
}
