package io.github.kreza6173pixel.pulsebattery.drain

import android.content.Context

/** Persists the current period baseline and the one-time star prompt flag. */
class BaselineStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Baseline? = DrainReport.decode(prefs.getString(KEY_BASELINE, null))

    fun save(b: Baseline) {
        prefs.edit().putString(KEY_BASELINE, DrainReport.encode(b)).apply()
    }

    var starPromptShown: Boolean
        get() = prefs.getBoolean(KEY_STAR, false)
        set(value) {
            prefs.edit().putBoolean(KEY_STAR, value).apply()
        }

    private companion object {
        const val PREFS = "drain_report"
        const val KEY_BASELINE = "baseline"
        const val KEY_STAR = "star_prompt_shown"
    }
}
