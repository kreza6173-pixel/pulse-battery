package io.github.kreza6173pixel.pulsebattery.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeGateTest {

    /** Shapes taken from the reference phone while plugged in and charging. */
    private val openProbe = """
        input_suspend=0
        capacity=80
        status=Charging
        current_now=512000
    """.trimIndent()

    private val pausedProbe = """
        input_suspend=1
        capacity=80
        status=Not charging
        current_now=0
    """.trimIndent()

    private val refusedProbe = """
        input_suspend=/system/bin/sh: cat: input_suspend: Permission denied
        capacity=80
        status=Charging
        current_now=/system/bin/sh: cat: current_now: Permission denied
    """.trimIndent()

    @Test
    fun zeroMeansTheGateIsOpen() {
        val snapshot = resolveGateSnapshot(openProbe, writable = true)
        assertEquals(GateState.OPEN, snapshot.state)
        assertEquals("0", snapshot.rawSuspend)
    }

    @Test
    fun oneMeansChargingIsHeldOff() {
        val snapshot = resolveGateSnapshot(pausedProbe, writable = true)
        assertEquals(GateState.PAUSED, snapshot.state)
    }

    @Test
    fun anUnreadableNodeIsUnknownRatherThanOpen() {
        val snapshot = resolveGateSnapshot(refusedProbe, writable = false)
        assertEquals(GateState.UNKNOWN, snapshot.state)
        assertNull(snapshot.currentMicroAmps)
    }

    @Test
    fun anUnexpectedValueIsAlsoUnknown() {
        assertEquals(GateState.UNKNOWN, GateParsers.stateOf("2"))
        assertEquals(GateState.UNKNOWN, GateParsers.stateOf("yes"))
        assertEquals(GateState.UNKNOWN, GateParsers.stateOf(""))
        assertEquals(GateState.UNKNOWN, GateParsers.stateOf(null))
    }

    @Test
    fun theOtherNodesAreStillParsedWhenTheGateIsRefused() {
        val snapshot = resolveGateSnapshot(refusedProbe, writable = false)
        assertEquals(80, snapshot.capacityPercent)
        assertEquals("Charging", snapshot.rawStatus)
    }

    @Test
    fun levelAndCurrentAreParsed() {
        val snapshot = resolveGateSnapshot(openProbe, writable = true)
        assertEquals(80, snapshot.capacityPercent)
        assertEquals(512000L, snapshot.currentMicroAmps)
    }

    @Test
    fun theWriteCommandIsBuiltFromConstantsOnly() {
        assertEquals(
            "echo 1 > /sys/class/power_supply/battery/input_suspend",
            GateParsers.writeCommand(SUSPEND_ON),
        )
        assertEquals(
            "echo 0 > /sys/class/power_supply/battery/input_suspend",
            GateParsers.writeCommand(SUSPEND_OFF),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun noOtherValueCanBeWritten() {
        GateParsers.writeCommand(2)
    }

    @Test
    fun theExpectedStateFollowsTheWrittenValue() {
        assertEquals(GateState.PAUSED, GateParsers.expectedState(SUSPEND_ON))
        assertEquals(GateState.OPEN, GateParsers.expectedState(SUSPEND_OFF))
    }

    @Test
    fun theStatusLineIsReadLeniently() {
        assertTrue(GateParsers.statusAgreesPaused("Not charging"))
        assertTrue(GateParsers.statusAgreesPaused(" discharging "))
        assertFalse(GateParsers.statusAgreesPaused("Charging"))
        assertTrue(GateParsers.statusAgreesOpen("Charging"))
        assertFalse(GateParsers.statusAgreesOpen("Full"))
    }

    @Test
    fun theProbeOnlyEverReadsTheFixedNodeList() {
        val probe = GateRepository.PROBE_COMMAND
        assertTrue(probe.startsWith("for f in "))
        assertTrue(probe.contains("2>&1"))
        GATE_NODES.forEach { assertTrue(it, probe.contains(it)) }
        assertEquals(4, GATE_NODES.size)
    }

    @Test
    fun theNodePathIsFixed() {
        assertEquals("/sys/class/power_supply/battery/input_suspend", INPUT_SUSPEND_PATH)
        assertEquals("input_suspend", NODE_INPUT_SUSPEND)
    }

    @Test
    fun theResetCommandTouchesStatisticsOnly() {
        assertEquals("dumpsys batterystats --reset", GateRepository.RESET_COMMAND)
    }

    @Test
    fun writabilityIsCarriedIntoTheSnapshot() {
        assertTrue(resolveGateSnapshot(openProbe, writable = true).writable)
        assertFalse(resolveGateSnapshot(openProbe, writable = false).writable)
    }

    @Test
    fun theTwoGateValuesAreZeroAndOne() {
        assertEquals(1, SUSPEND_ON)
        assertEquals(0, SUSPEND_OFF)
    }

    @Test
    fun anEmptyProbeGivesAnUnknownGateWithoutCrashing() {
        val snapshot = resolveGateSnapshot("", writable = true)
        assertEquals(GateState.UNKNOWN, snapshot.state)
        assertEquals("", snapshot.rawSuspend)
        assertNull(snapshot.capacityPercent)
    }
}
