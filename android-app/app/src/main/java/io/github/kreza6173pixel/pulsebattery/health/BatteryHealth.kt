package io.github.kreza6173pixel.pulsebattery.health

/**
 * Pure model of the kernel power-supply nodes. No Android imports, so every rule here is
 * unit-tested on the JVM.
 *
 * Node names come from the reference phone (Redmi Note 14, tanzanite, mt6789, HyperOS 3) and
 * are a fixed list: nothing user-supplied ever becomes part of a path.
 */

/** Directory the nodes live in. A constant, never built from input. */
const val POWER_SUPPLY_PATH = "/sys/class/power_supply/battery"

/** One node as it came back, plus whether it could be read at all. */
data class HealthNode(
    val name: String,
    val raw: String,
    val readable: Boolean,
)

/**
 * What the card shows. Every derived number keeps the raw values next to it, so a kernel that
 * reports a different unit is obvious instead of silently wrong.
 */
data class BatteryHealth(
    val nodes: List<HealthNode>,
    val cycleCount: Int?,
    val fullMicroAmpHours: Long?,
    val designMicroAmpHours: Long?,
    val healthPercent: Double?,
    val currentMicroAmps: Long?,
    val temperatureTenthsC: Int?,
    val capacityPercent: Int?,
) {
    val readableCount: Int get() = nodes.count { it.readable }
}

object HealthParsers {

    /**
     * The probe labels every line as `node=value` and folds stderr into the value, so a node
     * that cannot be read still produces a line. Order is never relied upon.
     */
    fun parseNodes(output: String): List<HealthNode> {
        val nodes = ArrayList<HealthNode>()
        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            val separator = trimmed.indexOf('=')
            if (separator <= 0) continue
            val name = trimmed.substring(0, separator)
            val raw = trimmed.substring(separator + 1).trim()
            nodes.add(HealthNode(name, raw, isReadable(raw)))
        }
        return nodes
    }

    /**
     * A node is readable when it produced a value rather than a kernel or shell complaint.
     * Checked by message because the exit code of the whole loop says nothing about one node.
     */
    fun isReadable(raw: String): Boolean {
        if (raw.isEmpty()) return false
        val lower = raw.lowercase()
        return REFUSAL_MARKERS.none { lower.contains(it) }
    }

    fun longOf(nodes: List<HealthNode>, name: String): Long? =
        nodes.firstOrNull { it.name == name && it.readable }?.raw?.trim()?.toLongOrNull()

    fun intOf(nodes: List<HealthNode>, name: String): Int? =
        longOf(nodes, name)?.let { if (it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) it.toInt() else null }

    /**
     * Ratio of the two capacity nodes as a percentage. A ratio on purpose: it holds whatever
     * unit the kernel uses, as long as both nodes use the same one. Values that cannot be a
     * real battery are dropped rather than displayed.
     */
    fun healthPercent(full: Long?, design: Long?): Double? {
        if (full == null || design == null) return null
        if (full <= 0 || design <= 0) return null
        val percent = full.toDouble() * 100.0 / design.toDouble()
        return if (percent > MAX_PLAUSIBLE_PERCENT) null else percent
    }

    /**
     * Microamp hours to milliamp hours. Applied only to values large enough to be microamp
     * hours, so a kernel that already reports milliamp hours is shown unchanged.
     */
    fun toMilliAmpHours(value: Long?): Int? {
        if (value == null || value <= 0) return null
        return if (value >= MICRO_THRESHOLD) (value / 1000L).toInt() else value.toInt()
    }

    /** Tenths of a degree Celsius, the long-standing Android convention for this node. */
    fun toCelsius(tenths: Int?): Double? = tenths?.let { it / 10.0 }

    /** Microamps to milliamps. Negative means discharging, which is kept as-is. */
    fun toMilliAmps(microAmps: Long?): Int? {
        if (microAmps == null) return null
        val magnitude = if (microAmps < 0) -microAmps else microAmps
        return if (magnitude >= MICRO_THRESHOLD) (microAmps / 1000L).toInt() else microAmps.toInt()
    }

    private val REFUSAL_MARKERS = listOf(
        "denied",
        "no such file",
        "not a directory",
        "invalid argument",
        "i/o error",
        "operation not permitted",
    )

    /** Above this, a capacity or current reading is microamp based rather than milliamp. */
    private const val MICRO_THRESHOLD = 10_000L

    /** A full charge far above the design capacity means the units do not match. */
    private const val MAX_PLAUSIBLE_PERCENT = 200.0
}

/** Builds the model from one probe output. */
fun resolveBatteryHealth(output: String): BatteryHealth {
    val nodes = HealthParsers.parseNodes(output)
    val full = HealthParsers.longOf(nodes, NODE_CHARGE_FULL)
    val design = HealthParsers.longOf(nodes, NODE_CHARGE_FULL_DESIGN)
    return BatteryHealth(
        nodes = nodes,
        cycleCount = HealthParsers.intOf(nodes, NODE_CYCLE_COUNT),
        fullMicroAmpHours = full,
        designMicroAmpHours = design,
        healthPercent = HealthParsers.healthPercent(full, design),
        currentMicroAmps = HealthParsers.longOf(nodes, NODE_CURRENT_NOW),
        temperatureTenthsC = HealthParsers.intOf(nodes, NODE_TEMP),
        capacityPercent = HealthParsers.intOf(nodes, NODE_CAPACITY),
    )
}

const val NODE_CYCLE_COUNT = "cycle_count"
const val NODE_CHARGE_FULL = "charge_full"
const val NODE_CHARGE_FULL_DESIGN = "charge_full_design"
const val NODE_CAPACITY = "capacity"
const val NODE_TEMP = "temp"
const val NODE_VOLTAGE_NOW = "voltage_now"
const val NODE_CURRENT_NOW = "current_now"
const val NODE_STATUS = "status"
const val NODE_HEALTH = "health"
const val NODE_SOH_AGING = "soh20_aging"
const val NODE_FIRST_USAGE = "first_usage_date"
const val NODE_MANUFACTURING = "manufacturing_date"
const val NODE_MODEL = "model_name"
const val NODE_CHARGE_COUNTER = "charge_counter"

/**
 * Every node the probe asks for, in display order. Hardcoded, so the probe command contains
 * no value that came from outside the app.
 */
val HEALTH_NODES = listOf(
    NODE_CYCLE_COUNT,
    NODE_CHARGE_FULL,
    NODE_CHARGE_FULL_DESIGN,
    NODE_CHARGE_COUNTER,
    NODE_CAPACITY,
    NODE_SOH_AGING,
    NODE_TEMP,
    NODE_VOLTAGE_NOW,
    NODE_CURRENT_NOW,
    NODE_STATUS,
    NODE_HEALTH,
    NODE_MODEL,
    NODE_FIRST_USAGE,
    NODE_MANUFACTURING,
)
