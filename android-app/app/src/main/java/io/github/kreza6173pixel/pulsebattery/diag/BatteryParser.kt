package io.github.kreza6173pixel.pulsebattery.diag

/** android.os.BatteryManager BATTERY_STATUS_* values 1..5. */
enum class BatteryStatus { UNKNOWN, CHARGING, DISCHARGING, NOT_CHARGING, FULL }

/** android.os.BatteryManager BATTERY_HEALTH_* values 1..7. */
enum class BatteryHealth { UNKNOWN, GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, FAILURE, COLD }

data class BatterySnapshot(
    val level: Int?,
    val scale: Int?,
    val status: BatteryStatus,
    val health: BatteryHealth,
    val acPowered: Boolean,
    val usbPowered: Boolean,
    val wirelessPowered: Boolean,
    val dockPowered: Boolean,
    val voltageMv: Int?,
    val temperatureTenthsC: Int?,
    val technology: String?,
    val chargeCounter: Long?,
    val present: Boolean?,
) {
    val percent: Int?
        get() = if (level != null && scale != null && scale > 0) (level * 100) / scale else null

    val pluggedIn: Boolean get() = acPowered || usbPowered || wirelessPowered || dockPowered

    val temperatureC: Double? get() = temperatureTenthsC?.let { it / 10.0 }
}

/**
 * Parses `dumpsys battery`. Pure; tested against real Xiaomi / Android 16 output.
 *
 * Only the `Current Battery Service state:` block is read. It ends at the next line that does
 * not start with whitespace, because vendors append their own sections (Xiaomi:
 * `MiuiBatteryService first usage time:`). The first occurrence of a key wins, so
 * `voltage: 3678` is used and `The last voltage value sent via ...: 3666` is ignored.
 */
object BatteryParser {

    private const val HEADER = "Current Battery Service state"

    private val LINE = Regex("""^\s+([A-Za-z][A-Za-z ]*?):\s*(.*)$""")

    fun parse(output: String): BatterySnapshot? {
        val values = HashMap<String, String>()
        var inSection = false
        for (line in output.lineSequence()) {
            if (!inSection) {
                if (line.trim().startsWith(HEADER)) inSection = true
                continue
            }
            if (line.isNotEmpty() && !line[0].isWhitespace()) break
            val m = LINE.matchEntire(line) ?: continue
            val key = m.groupValues[1].trim().lowercase()
            if (key !in values) values[key] = m.groupValues[2].trim()
        }
        if (!inSection || values.isEmpty()) return null

        fun int(key: String): Int? = values[key]?.toIntOrNull()
        fun bool(key: String): Boolean = values[key] == "true"

        return BatterySnapshot(
            level = int("level"),
            scale = int("scale"),
            status = statusOf(int("status")),
            health = healthOf(int("health")),
            acPowered = bool("ac powered"),
            usbPowered = bool("usb powered"),
            wirelessPowered = bool("wireless powered"),
            dockPowered = bool("dock powered"),
            voltageMv = int("voltage"),
            temperatureTenthsC = int("temperature"),
            technology = values["technology"]?.takeIf { it.isNotEmpty() },
            chargeCounter = values["charge counter"]?.toLongOrNull(),
            present = values["present"]?.let { it == "true" },
        )
    }

    private fun statusOf(code: Int?): BatteryStatus = when (code) {
        2 -> BatteryStatus.CHARGING
        3 -> BatteryStatus.DISCHARGING
        4 -> BatteryStatus.NOT_CHARGING
        5 -> BatteryStatus.FULL
        else -> BatteryStatus.UNKNOWN
    }

    private fun healthOf(code: Int?): BatteryHealth = when (code) {
        2 -> BatteryHealth.GOOD
        3 -> BatteryHealth.OVERHEAT
        4 -> BatteryHealth.DEAD
        5 -> BatteryHealth.OVER_VOLTAGE
        6 -> BatteryHealth.FAILURE
        7 -> BatteryHealth.COLD
        else -> BatteryHealth.UNKNOWN
    }
}
