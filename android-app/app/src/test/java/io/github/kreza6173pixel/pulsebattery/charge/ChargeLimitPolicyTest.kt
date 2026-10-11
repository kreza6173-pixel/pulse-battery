package io.github.kreza6173pixel.pulsebattery.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeLimitPolicyTest {

    private val on = ChargeLimitSettings(enabled = true, limitPercent = 80, resumePercent = 70)

    private fun inputs(
        level: Int? = 50,
        gate: GateState = GateState.OPEN,
        plugged: Boolean = true,
    ) = LimitInputs(level, gate, plugged)

    @Test
    fun reachingTheLimitClosesTheGate() {
        assertEquals(LimitDecision.PAUSE, ChargeLimitPolicy.decide(on, inputs(level = 80)))
        assertEquals(LimitDecision.PAUSE, ChargeLimitPolicy.decide(on, inputs(level = 95)))
    }

    @Test
    fun belowTheLimitNothingHappens() {
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, inputs(level = 79)))
    }

    @Test
    fun fallingToTheResumeThresholdOpensTheGate() {
        val paused = inputs(level = 70, gate = GateState.PAUSED)
        assertEquals(LimitDecision.RESUME, ChargeLimitPolicy.decide(on, paused))
    }

    @Test
    fun insideTheBandTheGateIsLeftAlone() {
        val paused = inputs(level = 75, gate = GateState.PAUSED)
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, paused))
        val open = inputs(level = 75, gate = GateState.OPEN)
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, open))
    }

    @Test
    fun anAlreadyClosedGateIsNotClosedAgain() {
        val paused = inputs(level = 90, gate = GateState.PAUSED)
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, paused))
    }

    @Test
    fun anAlreadyOpenGateIsNotOpenedAgain() {
        val open = inputs(level = 50, gate = GateState.OPEN)
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, open))
    }

    @Test
    fun unpluggingAlwaysOpensAClosedGate() {
        val unplugged = inputs(level = 90, gate = GateState.PAUSED, plugged = false)
        assertEquals(LimitDecision.RESUME, ChargeLimitPolicy.decide(on, unplugged))
    }

    @Test
    fun unpluggingOpensTheGateEvenWhenTheFeatureIsOff() {
        val off = ChargeLimitSettings(enabled = false)
        val unplugged = inputs(level = 90, gate = GateState.PAUSED, plugged = false)
        assertEquals(LimitDecision.RESUME, ChargeLimitPolicy.decide(off, unplugged))
    }

    @Test
    fun unpluggedWithAnOpenGateDoesNothing() {
        val unplugged = inputs(level = 90, gate = GateState.OPEN, plugged = false)
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, unplugged))
    }

    @Test
    fun anUnknownGateIsNeverTouched() {
        val unknown = inputs(level = 90, gate = GateState.UNKNOWN)
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, unknown))
    }

    @Test
    fun anUnreadableLevelIsNeverGuessed() {
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, inputs(level = null)))
    }

    @Test
    fun animpossibleLevelIsIgnored() {
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, inputs(level = 101)))
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(on, inputs(level = -1)))
    }

    @Test
    fun disabledMeansTheGateIsLeftAlone() {
        val off = ChargeLimitSettings(enabled = false)
        assertEquals(LimitDecision.LEAVE, ChargeLimitPolicy.decide(off, inputs(level = 95)))
    }

    @Test
    fun theLimitIsClampedIntoTheOfferedRange() {
        assertEquals(LIMIT_MAX, ChargeLimitPolicy.sanitise(on.copy(limitPercent = 100)).limitPercent)
        assertEquals(LIMIT_MIN, ChargeLimitPolicy.sanitise(on.copy(limitPercent = 5)).limitPercent)
    }

    @Test
    fun theResumeThresholdIsKeptBelowTheLimit() {
        val crossed = ChargeLimitPolicy.sanitise(on.copy(limitPercent = 60, resumePercent = 90))
        assertEquals(60, crossed.limitPercent)
        assertEquals(55, crossed.resumePercent)
        assertTrue(crossed.resumePercent < crossed.limitPercent)
    }

    @Test
    fun theGapIsAtLeastTheMinimumHysteresis() {
        val tight = ChargeLimitPolicy.sanitise(on.copy(limitPercent = 80, resumePercent = 79))
        assertEquals(80 - MIN_HYSTERESIS, tight.resumePercent)
    }

    @Test
    fun theResumeThresholdNeverGoesBelowItsFloor() {
        val low = ChargeLimitPolicy.sanitise(on.copy(limitPercent = LIMIT_MIN, resumePercent = 1))
        assertEquals(RESUME_MIN, low.resumePercent)
    }

    @Test
    fun theOfferedResumeRangeStopsBelowTheLimit() {
        val range = ChargeLimitPolicy.resumeRange(80)
        assertEquals(RESUME_MIN, range.first)
        assertEquals(75, range.last)
    }

    @Test
    fun theOfferedRangeStaysValidAtTheLowestLimit() {
        val range = ChargeLimitPolicy.resumeRange(LIMIT_MIN)
        assertTrue(range.first <= range.last)
        assertEquals(RESUME_MIN, range.first)
    }

    @Test
    fun theServiceKeepsRunningWhileEnabled() {
        assertTrue(ChargeLimitPolicy.shouldKeepRunning(on, GateState.OPEN))
        assertTrue(ChargeLimitPolicy.shouldKeepRunning(on, GateState.PAUSED))
    }

    @Test
    fun theServiceAlsoKeepsRunningToUndoAClosedGate() {
        val off = ChargeLimitSettings(enabled = false)
        assertTrue(ChargeLimitPolicy.shouldKeepRunning(off, GateState.PAUSED))
        assertFalse(ChargeLimitPolicy.shouldKeepRunning(off, GateState.OPEN))
    }

    @Test
    fun theDefaultsAreAUsablePair() {
        val defaults = ChargeLimitPolicy.sanitise(ChargeLimitSettings())
        assertEquals(DEFAULT_LIMIT, defaults.limitPercent)
        assertEquals(DEFAULT_RESUME, defaults.resumePercent)
        assertFalse(defaults.enabled)
        assertFalse(defaults.restoreOnBoot)
    }
}
