package io.github.kreza6173pixel.pulsebattery.charge

import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.exec.ExecOutcome
import io.github.kreza6173pixel.pulsebattery.exec.ShellQuoting

/**
 * Reads and writes the ROM charging mode. Every call blocks on the UserService, so call from
 * Dispatchers.IO.
 *
 * Shell access is enough for this: the write was proven from a plain adb shell on the
 * reference phone. Root changes nothing here, which is why the control is not gated on it.
 */
class ChargeRepository(private val bridge: ExecBridge) {

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

    /** One probe: the current key value plus the ROM markers used for the capability check. */
    fun read(): DiagResult<ChargeState> {
        val done = when (val result = sh(PROBE_COMMAND)) {
            is Shell.Failed -> return DiagResult.Error(result.message, "")
            is Shell.Done -> result
        }
        val raw = if (done.err.isEmpty()) done.out else done.out + "\n" + done.err
        val values = ChargeParsers.parseLabelled(done.out)
        if (!values.containsKey(LABEL_KEY)) {
            return DiagResult.Error("the probe returned no $LABEL_KEY line", raw)
        }
        val state = resolveChargeState(values[LABEL_KEY], ChargeParsers.romMarkerOf(values))
        return DiagResult.Ok(state, raw)
    }

    /**
     * Writes one mode and verifies it.
     *
     * A ROM that does not implement the requested mode silently keeps the old value, which is
     * exactly what another HyperOS 3 device showed, so success is claimed only when the
     * read-back matches.
     */
    fun setMode(target: ChargeMode): ChargeActionResult {
        val before = currentMode()
        val command = "settings put secure " + ShellQuoting.quote(CHARGE_MODE_SETTING) +
            " " + target.keyValue
        when (val result = sh(command)) {
            is Shell.Failed -> return ChargeActionResult(false, result.message, before)
            is Shell.Done -> if (result.exit != 0) {
                val detail = (result.err + " " + result.out).trim()
                return ChargeActionResult(false, "exit ${result.exit}: $detail", before)
            }
        }
        val after = readKeyValue()
        return when {
            after == null -> ChargeActionResult(
                false,
                "wrote ${target.keyValue}, but the setting could not be read back",
                before,
            )
            after == target.keyValue -> ChargeActionResult(
                true,
                "$CHARGE_MODE_SETTING = $after",
                before,
            )
            else -> ChargeActionResult(
                false,
                "asked ${target.keyValue}, the ROM reports $after",
                before,
            )
        }
    }

    private fun currentMode(): ChargeMode? = ChargeMode.fromKeyValue(readKeyValue())

    private fun readKeyValue(): Int? =
        when (val result = sh(GET_COMMAND)) {
            is Shell.Failed -> null
            is Shell.Done -> if (result.exit != 0) null else ChargeParsers.modeValueOf(result.out)
        }

    companion object {
        val GET_COMMAND = "settings get secure " + ShellQuoting.quote(CHARGE_MODE_SETTING)

        /**
         * Labelled so the parser never depends on line order, and every reading is optional.
         * The ROM markers are read because the setting is Xiaomi-specific: on any other ROM
         * the control must not be offered at all.
         */
        val PROBE_COMMAND = listOf(
            "echo \"$LABEL_KEY=\$($GET_COMMAND 2>/dev/null)\"",
            "echo \"$LABEL_MI_OS=\$(getprop ro.mi.os.version.incremental)\"",
            "echo \"$LABEL_MI_OS_NAME=\$(getprop ro.mi.os.version.name)\"",
            "echo \"$LABEL_MIUI=\$(getprop ro.miui.ui.version.name)\"",
        ).joinToString("; ")

        private const val TIMEOUT_MS = 10_000
    }
}
