package io.github.kreza6173pixel.pulsebattery.health

import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.exec.ExecOutcome

/**
 * Read-only access to the kernel power-supply nodes. Every call blocks on the UserService, so
 * call from Dispatchers.IO.
 *
 * There is no write path in this class on purpose. Charge thresholds live behind their own
 * feature, with their own confirmation and restore rules; mixing them into a health readout
 * would make a read screen capable of changing hardware state.
 */
class HealthRepository(private val bridge: ExecBridge) {

    fun read(): DiagResult<BatteryHealth> =
        when (val outcome = bridge.execBlocking(PROBE_COMMAND, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> DiagResult.Error(outcome.message, "")
            is ExecOutcome.Completed -> {
                val result = outcome.result
                val raw = if (result.stderr.isEmpty()) {
                    result.stdout
                } else {
                    result.stdout + "\n" + result.stderr
                }
                val health = resolveBatteryHealth(result.stdout)
                if (health.nodes.isEmpty()) {
                    DiagResult.Error("the probe returned no node lines", raw)
                } else {
                    DiagResult.Ok(health, raw)
                }
            }
        }

    companion object {
        /**
         * One loop over a fixed node list. stderr is folded into each value so a node the
         * current uid may not read becomes a row that says so, instead of vanishing and
         * leaving a gap that looks like a zero.
         */
        val PROBE_COMMAND: String = buildString {
            append("for f in ")
            append(HEALTH_NODES.joinToString(" "))
            append("; do echo \"\$f=\$(cat ")
            append(POWER_SUPPLY_PATH)
            append("/\$f 2>&1)\"; done")
        }

        private const val TIMEOUT_MS = 15_000
    }
}
