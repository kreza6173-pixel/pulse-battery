package io.github.kreza6173pixel.pulsebattery.charge

import android.content.Context

/**
 * Persists the charge-limit choice. Plain shared preferences: four small values, read on every
 * poll and written only when the user changes something.
 *
 * Everything read back out goes through [ChargeLimitPolicy.sanitise], so a file edited by hand
 * or left behind by an older version can never produce a pair of thresholds that would make
 * the gate flap.
 */
class ChargeLimitStore(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): ChargeLimitSettings = ChargeLimitPolicy.sanitise(
        ChargeLimitSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            limitPercent = prefs.getInt(KEY_LIMIT, DEFAULT_LIMIT),
            resumePercent = prefs.getInt(KEY_RESUME, DEFAULT_RESUME),
            restoreOnBoot = prefs.getBoolean(KEY_RESTORE_ON_BOOT, false),
        )
    )

    fun write(settings: ChargeLimitSettings): ChargeLimitSettings {
        val clean = ChargeLimitPolicy.sanitise(settings)
        prefs.edit()
            .putBoolean(KEY_ENABLED, clean.enabled)
            .putInt(KEY_LIMIT, clean.limitPercent)
            .putInt(KEY_RESUME, clean.resumePercent)
            .putBoolean(KEY_RESTORE_ON_BOOT, clean.restoreOnBoot)
            .apply()
        return clean
    }

    private companion object {
        const val FILE = "charge_limit"
        const val KEY_ENABLED = "enabled"
        const val KEY_LIMIT = "limit_percent"
        const val KEY_RESUME = "resume_percent"
        const val KEY_RESTORE_ON_BOOT = "restore_on_boot"
    }
}
