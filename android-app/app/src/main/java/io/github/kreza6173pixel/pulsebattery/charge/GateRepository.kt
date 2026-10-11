package io.github.kreza6173pixel.pulsebattery.charge

import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.exec.ExecOutcome
import io.github.kreza6173pixel.pulsebattery.health.POWER_SUPPLY_PATH

/**
 * Manual charge gate plus the battery-stats reset. Every call blocks on the UserService, so
 * call from Dispatchers.IO.
 *
 * [writable] says whether the service runs with a uid that may write the node. It is passed
 * in rather than guessed here, and a write is refused outright when it is false, so the app
 * never produces a command it already knows cannot work.
 *
 * Nothing in this class schedules anything. The gate changes only when a caller asks, which
 * is why this half needs no background component at all.
 */
class GateRepository(
    private val bridge: ExecBridge,
    private val writable: Boolean,
) {

    private sealed interface Shell {
        data class Done(val exit: Int, val out: String, val err: String) : Shell
        data class Failed(val message: String) : Shell
    }

    private fun sh(command: String): Shell =
        when (val outcome = bridge.execBlocking(command, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> Shell.Failed(outcome.message)
            is ExecOutcome.Completed -> Shell.Done(
                outcome.result.exitCode,
                outcome.result.stdout,
                outcome.result.stderr,
            )
        }

    fun read(): DiagResult<GateSnapshot> {
        val done = when (val result = sh(PROBE_COMMAND)) {
            is Shell.Failed -> return DiagResult.Error(result.message, "")
            is Shell.Done -> result
        }
        val raw = if (done.err.isEmpty()) done.out else done.out + "\n" + done.err
        val snapshot = resolveGateSnapshot(done.out, writable)
        return DiagResult.Ok(snapshot, raw)
    }

    /** Cuts the charger input. */
    fun pause(): GateActionResult = write(SUSPEND_ON)

    /** Restores the charger input. */
    fun resume(): GateActionResult = write(SUSPEND_OFF)

    /**
     * Writes the node and verifies it.
     *
     * The node value decides the verdict; the charging status is only quoted alongside it,
     * because ROM wording for that line is not consistent enough to judge a write by.
     */
    private fun write(value: Int): GateActionResult {
        if (!writable) {
            return GateActionResult(
                false,
                "refused: writing $NODE_INPUT_SUSPEND needs the service to run as root",
                GateState.UNKNOWN,
            )
        }
        val command = GateParsers.writeCommand(value)
        when (val result = sh(command)) {
            is Shell.Failed -> return GateActionResult(false, result.message, GateState.UNKNOWN)
            is Shell.Done -> if (result.exit != 0) {
                val detail = (result.err + " " + result.out).trim()
                return GateActionResult(false, "exit ${result.exit}: $detail", GateState.UNKNOWN)
            }
        }
        val after = when (val result = read()) {
            is DiagResult.Error -> return GateActionResult(
                false,
                "wrote $value, but the node could not be read back: ${result.message}",
                GateState.UNKNOWN,
            )
            is DiagResult.Ok -> result.value
        }
        val expected = GateParsers.expectedState(value)
        val agrees = if (value == SUSPEND_ON) {
            GateParsers.statusAgreesPaused(after.rawStatus)
        } else {
            GateParsers.statusAgreesOpen(after.rawStatus)
        }
        val statusNote = if (agrees) {
            "status: ${after.rawStatus}"
        } else {
            "status still reads ${after.rawStatus}, which the ROM may word differently"
        }
        return if (after.state == expected) {
            GateActionResult(true, "$NODE_INPUT_SUSPEND = $value, $statusNote", after.state)
        } else {
            GateActionResult(
                false,
                "wrote $value, the node reads ${after.rawSuspend.ifEmpty { "nothing" }}",
                after.state,
            )
        }
    }

    /**
     * Clears the accumulated battery statistics, so the next overnight drain report starts
     * from a known point. Needs no root and changes no hardware state.
     */
    fun resetBatteryStats(): GateActionResult =
        when (val result = sh(RESET_COMMAND)) {
            is Shell.Failed -> GateActionResult(false, result.message, GateState.UNKNOWN)
            is Shell.Done -> {
                val detail = (result.out + " " + result.err).trim()
                GateActionResult(
                    result.exit == 0,
                    detail.ifEmpty { "exit ${result.exit}" },
                    GateState.UNKNOWN,
                )
            }
        }

    companion object {
        /**
         * Labelled so the parser never depends on line order, with stderr folded into each
         * value so an unreadable node says so instead of leaving a gap.
         */
        val PROBE_COMMAND: String = buildString {
            append("for f in ")
            append(GATE_NODES.joinToString(" "))
            append("; do echo \"\$f=\$(cat ")
            append(POWER_SUPPLY_PATH)
            append("/\$f 2>&1)\"; done")
        }

        const val RESET_COMMAND = "dumpsys batterystats --reset"

        private const val TIMEOUT_MS = 15_000
    }
}
