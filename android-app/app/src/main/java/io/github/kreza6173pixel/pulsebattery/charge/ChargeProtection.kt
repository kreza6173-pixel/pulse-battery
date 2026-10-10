package io.github.kreza6173pixel.pulsebattery.charge

/**
 * Pure model of the HyperOS charging-mode setting. No Android imports, so every rule below is
 * unit-tested on the JVM.
 *
 * The ROM keeps the choice in one secure setting. Values were confirmed on the reference phone
 * (Redmi Note 14, tanzanite, HyperOS 3, Android 16): writing the key from outside the Settings
 * app really stops and resumes charging, and the Settings UI follows the write.
 */
enum class ChargeMode(val keyValue: Int) {
    /** No cap. */
    CHARGE_FULLY(0),

    /** The ROM decides when to hold the charge, based on usage habits. */
    INTELLIGENT(1),

    /** Hold at the cap the ROM defines. Fixed at 80 percent on the reference phone. */
    PROTECTED(2),
    ;

    companion object {
        fun fromKeyValue(value: Int?): ChargeMode? = entries.firstOrNull { it.keyValue == value }
    }
}

/** How much is actually known about this ROM. */
enum class ChargeSupport {
    /** No Xiaomi ROM marker: the setting does not belong to this ROM. Hide the control. */
    UNSUPPORTED,

    /**
     * A Xiaomi ROM, but the key holds no value yet. An absent key reads as intelligent
     * charging, which is the factory state, so the control is offered and labelled as not
     * yet confirmed on this build.
     */
    FACTORY_DEFAULT,

    /** The key holds a value: the feature exists here. */
    SUPPORTED,
}

/** Everything one probe can tell us. [rawKeyValue] and [romMarker] are shown, never trusted. */
data class ChargeState(
    val support: ChargeSupport,
    val mode: ChargeMode?,
    val rawKeyValue: String,
    val romMarker: String,
)

/** Outcome of a write. [ok] is true only when a read-back confirmed the new value. */
data class ChargeActionResult(
    val ok: Boolean,
    val message: String,
    val previous: ChargeMode? = null,
)

/** The one secure setting this feature touches. A constant: never built from user input. */
const val CHARGE_MODE_SETTING = "security_pc_secure_protect_mode_key"

/** Labels used by the probe command and expected back by [ChargeParsers.parseLabelled]. */
const val LABEL_KEY = "key"
const val LABEL_MI_OS = "mios"
const val LABEL_MI_OS_NAME = "miosname"
const val LABEL_MIUI = "miui"

object ChargeParsers {

    /**
     * Splits `label=value` lines. The probe labels its own output so one command can carry
     * several readings and a missing one is simply absent instead of shifting the others.
     */
    fun parseLabelled(output: String): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            val separator = trimmed.indexOf('=')
            if (separator <= 0) continue
            values[trimmed.substring(0, separator)] = trimmed.substring(separator + 1).trim()
        }
        return values
    }

    /**
     * `settings get` prints the literal word null for a setting that was never written, and
     * `getprop` prints an empty line for a property that does not exist. Both mean absent.
     */
    fun isAbsent(raw: String?): Boolean {
        val value = raw?.trim() ?: return true
        return value.isEmpty() || value == "null"
    }

    /** The numeric value of the key, or null when absent or not a number. */
    fun modeValueOf(raw: String?): Int? =
        if (isAbsent(raw)) null else raw?.trim()?.toIntOrNull()

    /** First non-empty ROM marker, used only to decide whether this is a Xiaomi ROM. */
    fun romMarkerOf(values: Map<String, String>): String =
        listOf(LABEL_MI_OS, LABEL_MI_OS_NAME, LABEL_MIUI)
            .asSequence()
            .map { values[it] }
            .firstOrNull { !isAbsent(it) }
            ?.trim()
            .orEmpty()
}

/**
 * Decides what to show.
 *
 * A value in the key is the strongest signal and outranks everything. Without one, a Xiaomi
 * ROM marker is enough to offer the control, because the factory state of this key is "absent"
 * and a write is both reversible and verified by a read-back.
 */
fun resolveChargeState(rawKeyValue: String?, romMarker: String): ChargeState {
    val numeric = ChargeParsers.modeValueOf(rawKeyValue)
    val support = when {
        numeric != null -> ChargeSupport.SUPPORTED
        romMarker.isNotEmpty() -> ChargeSupport.FACTORY_DEFAULT
        else -> ChargeSupport.UNSUPPORTED
    }
    return ChargeState(
        support = support,
        mode = ChargeMode.fromKeyValue(numeric),
        rawKeyValue = rawKeyValue?.trim().orEmpty(),
        romMarker = romMarker,
    )
}
