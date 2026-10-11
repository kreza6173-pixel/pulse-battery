package io.github.kreza6173pixel.pulsebattery.charge

import io.github.kreza6173pixel.pulsebattery.health.HealthParsers
import io.github.kreza6173pixel.pulsebattery.health.POWER_SUPPLY_PATH

/**
 * Pure model of the charge gate. No Android imports, so every rule here is unit-tested.
 *
 * One kernel node decides whether the charger input reaches the battery. Writing 1 cuts it,
 * writing 0 restores it. The node and its two values were observed on the reference phone
 * (Redmi Note 14, tanzanite, mt6789), where another charge limiter drives exactly this path.
 */

/** The only node this feature writes. A constant: never built from input. */
const val NODE_INPUT_SUSPEND = "input_suspend"

/** Full path of that node. */
const val INPUT_SUSPEND_PATH = "$POWER_SUPPLY_PATH/$NODE_INPUT_SUSPEND"

const val SUSPEND_ON = 1
const val SUSPEND_OFF = 0

/** Where the gate currently stands. */
enum class GateState {
    /** The node reads 0: the charger input reaches the battery. */
    OPEN,

    /** The node reads 1: charging is held off by this node. */
    PAUSED,

    /** The node could not be read, or holds something unexpected. */
    UNKNOWN,
}

/** What one probe of the gate tells us. Raw values are kept so a surprise is visible. */
data class GateSnapshot(
    val state: GateState,
    val writable: Boolean,
    val rawSuspend: String,
    val rawStatus: String,
    val capacityPercent: Int?,
    val currentMicroAmps: Long?,
)

/** Outcome of a write. [ok] is true only when the node read back as requested. */
data class GateActionResult(
    val ok: Boolean,
    val message: String,
    val stateAfter: GateState,
)

object GateParsers {

    /** Maps the raw node content to a state. Anything unexpected stays UNKNOWN. */
    fun stateOf(raw: String?): GateState {
        if (raw == null || !HealthParsers.isReadable(raw)) return GateState.UNKNOWN
        return when (raw.trim()) {
            SUSPEND_OFF.toString() -> GateState.OPEN
            SUSPEND_ON.toString() -> GateState.PAUSED
            else -> GateState.UNKNOWN
        }
    }

    /** The state the node should report after writing [value]. */
    fun expectedState(value: Int): GateState =
        if (value == SUSPEND_ON) GateState.PAUSED else GateState.OPEN

    /**
     * The command that flips the gate. The path and both values are constants, so no part of
     * this string can come from outside the app.
     */
    fun writeCommand(value: Int): String {
        require(value == SUSPEND_ON || value == SUSPEND_OFF) { "invalid gate value" }
        return "echo $value > $INPUT_SUSPEND_PATH"
    }

    /**
     * True when the status line agrees that charging stopped. Advisory only: ROM wording
     * differs, so a disagreement is reported next to the node value instead of overriding it.
     */
    fun statusAgreesPaused(rawStatus: String): Boolean {
        val lower = rawStatus.trim().lowercase()
        return lower == "not charging" || lower == "discharging"
    }

    /** True when the status line agrees that charging resumed. */
    fun statusAgreesOpen(rawStatus: String): Boolean =
        rawStatus.trim().lowercase() == "charging"
}

/** Builds the snapshot from one labelled probe output. */
fun resolveGateSnapshot(output: String, writable: Boolean): GateSnapshot {
    val nodes = HealthParsers.parseNodes(output)
    val suspend = nodes.firstOrNull { it.name == NODE_INPUT_SUSPEND }
    val status = nodes.firstOrNull { it.name == GATE_NODE_STATUS }
    return GateSnapshot(
        state = GateParsers.stateOf(suspend?.raw),
        writable = writable,
        rawSuspend = suspend?.raw.orEmpty(),
        rawStatus = status?.raw.orEmpty(),
        capacityPercent = HealthParsers.intOf(nodes, GATE_NODE_CAPACITY),
        currentMicroAmps = HealthParsers.longOf(nodes, GATE_NODE_CURRENT),
    )
}

const val GATE_NODE_CAPACITY = "capacity"
const val GATE_NODE_STATUS = "status"
const val GATE_NODE_CURRENT = "current_now"

/** Nodes the gate probe reads. Hardcoded, in display order. */
val GATE_NODES = listOf(
    NODE_INPUT_SUSPEND,
    GATE_NODE_CAPACITY,
    GATE_NODE_STATUS,
    GATE_NODE_CURRENT,
)
