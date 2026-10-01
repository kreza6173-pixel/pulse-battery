package io.github.kreza6173pixel.pulsebattery.diag

import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.exec.ExecOutcome

/** Parsed value plus the raw output it came from, so the UI can offer Copy / Share. */
sealed interface DiagResult<out T> {
    val raw: String

    data class Ok<out T>(val value: T, override val raw: String) : DiagResult<T>

    data class Error(val message: String, override val raw: String) : DiagResult<Nothing>
}

/**
 * Read-only diagnostics. Every call blocks on the UserService, so call from Dispatchers.IO.
 * Commands are fixed constants: nothing user-supplied is ever interpolated.
 */
class DiagnosticsRepository(private val bridge: ExecBridge) {

    fun battery(): DiagResult<BatterySnapshot> =
        run(BATTERY_COMMAND, setOf(0)) { BatteryParser.parse(it) }

    /** grep exits 1 when nothing matches: that is a valid empty list, not an error. */
    fun wakeLocks(): DiagResult<List<WakeLockEntry>> =
        run(WAKE_LOCK_COMMAND, setOf(0, 1)) { WakeLockParser.parse(it) }

    private fun <T : Any> run(
        command: String,
        okExitCodes: Set<Int>,
        parse: (String) -> T?,
    ): DiagResult<T> = when (val outcome = bridge.execBlocking(command, TIMEOUT_MS)) {
        is ExecOutcome.Failed -> DiagResult.Error(outcome.message, "")
        is ExecOutcome.Completed -> {
            val r = outcome.result
            val raw = if (r.stderr.isEmpty()) r.stdout else r.stdout + "\n" + r.stderr
            if (r.exitCode !in okExitCodes) {
                DiagResult.Error("exit ${r.exitCode}", raw)
            } else {
                val parsed = parse(r.stdout)
                if (parsed == null) DiagResult.Error("unrecognised output", raw) else DiagResult.Ok(parsed, raw)
            }
        }
    }

    companion object {
        const val BATTERY_COMMAND = "dumpsys battery"

        /** Filtered on the device so the 64 KiB output cap of the service is never reached. */
        const val WAKE_LOCK_COMMAND = "dumpsys power | grep -E \"_WAKE_LOCK +'\""

        private const val TIMEOUT_MS = 10_000
    }
}
