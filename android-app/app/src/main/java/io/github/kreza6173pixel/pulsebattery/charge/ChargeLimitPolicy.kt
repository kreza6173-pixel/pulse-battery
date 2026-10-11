package io.github.kreza6173pixel.pulsebattery.charge

/**
 * Pure policy for the automatic charge limit. No Android imports, so every rule is covered by
 * unit tests on the JVM.
 *
 * The limit works by flipping the same kernel gate the manual buttons use. The only thing
 * added here is deciding when to flip it.
 */

/** Lowest limit the UI offers. Below this the feature stops being useful and starts annoying. */
const val LIMIT_MIN = 40

/** Highest limit. At 100 the gate would never close, so the feature is off instead. */
const val LIMIT_MAX = 95

/** Lowest resume threshold offered. */
const val RESUME_MIN = 20

/** Smallest gap kept between the limit and the resume threshold, in percent. */
const val MIN_HYSTERESIS = 5

const val DEFAULT_LIMIT = 80
const val DEFAULT_RESUME = 70

/** Persisted user choice. Plain values, so it can be built in a test without Android. */
data class ChargeLimitSettings(
    val enabled: Boolean = false,
    val limitPercent: Int = DEFAULT_LIMIT,
    val resumePercent: Int = DEFAULT_RESUME,
    /** Re-apply the limit after a reboot. Opt-in, because it implies a service at boot. */
    val restoreOnBoot: Boolean = false,
)

/** What the policy wants done to the gate right now. */
enum class LimitDecision {
    /** Close the gate: the level reached the limit. */
    PAUSE,

    /** Open the gate: the level fell to the resume threshold, or the charger was unplugged. */
    RESUME,

    /** Do nothing. The only decision taken when anything at all is uncertain. */
    LEAVE,
}

/** The facts the policy is allowed to look at. */
data class LimitInputs(
    val capacityPercent: Int?,
    val gate: GateState,
    val plugged: Boolean,
)

object ChargeLimitPolicy {

    /**
     * Forces a usable pair of thresholds.
     *
     * The limit is clamped into the offered range first, then the resume threshold is pushed
     * below it by at least [MIN_HYSTERESIS]. Without that gap the two thresholds could meet
     * and the gate would flip on every poll.
     */
    fun sanitise(settings: ChargeLimitSettings): ChargeLimitSettings {
        val limit = settings.limitPercent.coerceIn(LIMIT_MIN, LIMIT_MAX)
        val highestResume = limit - MIN_HYSTERESIS
        val resume = settings.resumePercent.coerceIn(RESUME_MIN, highestResume.coerceAtLeast(RESUME_MIN))
        return settings.copy(limitPercent = limit, resumePercent = resume)
    }

    /** Resume values the UI may offer for a given limit. */
    fun resumeRange(limitPercent: Int): IntRange {
        val limit = limitPercent.coerceIn(LIMIT_MIN, LIMIT_MAX)
        val highest = (limit - MIN_HYSTERESIS).coerceAtLeast(RESUME_MIN)
        return RESUME_MIN..highest
    }

    /**
     * Decides what to do with the gate.
     *
     * Order matters. Safety first: an unplugged charger with a closed gate is always opened,
     * because leaving it closed would silently break the next charge. Then the uncertain
     * cases, which always mean LEAVE. Only then the two thresholds.
     */
    fun decide(settings: ChargeLimitSettings, inputs: LimitInputs): LimitDecision {
        if (!inputs.plugged && inputs.gate == GateState.PAUSED) return LimitDecision.RESUME
        if (!settings.enabled) return LimitDecision.LEAVE
        if (inputs.gate == GateState.UNKNOWN) return LimitDecision.LEAVE
        val level = inputs.capacityPercent ?: return LimitDecision.LEAVE
        if (level !in 0..100) return LimitDecision.LEAVE
        if (!inputs.plugged) return LimitDecision.LEAVE

        val clean = sanitise(settings)
        return when {
            level >= clean.limitPercent && inputs.gate == GateState.OPEN -> LimitDecision.PAUSE
            level <= clean.resumePercent && inputs.gate == GateState.PAUSED -> LimitDecision.RESUME
            else -> LimitDecision.LEAVE
        }
    }

    /**
     * True when the service has any reason to keep polling. Used so the service can stop
     * itself instead of relying on the UI to remember to stop it.
     */
    fun shouldKeepRunning(settings: ChargeLimitSettings, gate: GateState): Boolean =
        settings.enabled || gate == GateState.PAUSED
}
