package io.github.kreza6173pixel.pulsebattery.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHealthTest {

    /** Shapes taken from the reference phone, including a node the shell uid may not read. */
    private val rootProbe = """
        cycle_count=42
        charge_full=4850000
        charge_full_design=5000000
        charge_counter=4283000
        capacity=80
        soh20_aging=97
        temp=330
        voltage_now=4228000
        current_now=-512000
        status=Charging
        health=Good
        model_name=BN5J
        first_usage_date=20260516
        manufacturing_date=20251201
    """.trimIndent()

    private val shellProbe = """
        cycle_count=42
        charge_full=4850000
        charge_full_design=5000000
        capacity=80
        temp=330
        current_now=/system/bin/sh: cat: current_now: Permission denied
        soh20_aging=cat: soh20_aging: No such file or directory
    """.trimIndent()

    @Test
    fun everyLabelledLineBecomesANode() {
        val health = resolveBatteryHealth(rootProbe)
        assertEquals(14, health.nodes.size)
        assertEquals(14, health.readableCount)
    }

    @Test
    fun aRefusedNodeIsMarkedUnreadable() {
        val health = resolveBatteryHealth(shellProbe)
        val current = health.nodes.first { it.name == NODE_CURRENT_NOW }
        assertFalse(current.readable)
        assertNull(health.currentMicroAmps)
    }

    @Test
    fun aMissingNodeIsAlsoUnreadable() {
        val health = resolveBatteryHealth(shellProbe)
        assertFalse(health.nodes.first { it.name == NODE_SOH_AGING }.readable)
    }

    @Test
    fun readableNodesStillWorkWhenOthersAreRefused() {
        val health = resolveBatteryHealth(shellProbe)
        assertEquals(42, health.cycleCount)
        assertEquals(80, health.capacityPercent)
        assertEquals(5, health.readableCount)
    }

    @Test
    fun healthIsTheRatioOfTheTwoCapacityNodes() {
        val health = resolveBatteryHealth(rootProbe)
        assertEquals(97.0, health.healthPercent!!, 0.01)
    }

    @Test
    fun healthIsDroppedWhenTheNumbersCannotBeABattery() {
        assertNull(HealthParsers.healthPercent(5_000_000L, 0L))
        assertNull(HealthParsers.healthPercent(0L, 5_000_000L))
        assertNull(HealthParsers.healthPercent(5_000_000L, 1_000L))
        assertNull(HealthParsers.healthPercent(null, 5_000_000L))
    }

    @Test
    fun microAmpHoursBecomeMilliAmpHours() {
        assertEquals(4850, HealthParsers.toMilliAmpHours(4_850_000L))
        assertEquals(5000, HealthParsers.toMilliAmpHours(5_000_000L))
    }

    @Test
    fun aKernelThatAlreadyReportsMilliAmpHoursIsLeftAlone() {
        assertEquals(4850, HealthParsers.toMilliAmpHours(4850L))
        assertNull(HealthParsers.toMilliAmpHours(0L))
        assertNull(HealthParsers.toMilliAmpHours(null))
    }

    @Test
    fun dischargingCurrentKeepsItsSign() {
        assertEquals(-512, HealthParsers.toMilliAmps(-512_000L))
        assertEquals(512, HealthParsers.toMilliAmps(512_000L))
        assertEquals(0, HealthParsers.toMilliAmps(0L))
        assertNull(HealthParsers.toMilliAmps(null))
    }

    @Test
    fun temperatureIsInTenthsOfADegree() {
        val health = resolveBatteryHealth(rootProbe)
        assertEquals(330, health.temperatureTenthsC)
        assertEquals(33.0, HealthParsers.toCelsius(health.temperatureTenthsC)!!, 0.01)
    }

    @Test
    fun refusalMessagesAreRecognisedWhateverTheirCase() {
        assertFalse(HealthParsers.isReadable("Permission denied"))
        assertFalse(HealthParsers.isReadable("PERMISSION DENIED"))
        assertFalse(HealthParsers.isReadable("No such file or directory"))
        assertFalse(HealthParsers.isReadable(""))
        assertTrue(HealthParsers.isReadable("42"))
        assertTrue(HealthParsers.isReadable("Good"))
    }

    @Test
    fun garbageLinesAreIgnored() {
        val health = resolveBatteryHealth("nonsense\n=orphan\ncycle_count=7")
        assertEquals(1, health.nodes.size)
        assertEquals(7, health.cycleCount)
    }

    @Test
    fun theProbeOnlyEverReadsTheFixedNodeList() {
        val probe = HealthRepository.PROBE_COMMAND
        assertTrue(probe.startsWith("for f in "))
        assertTrue(probe.contains(POWER_SUPPLY_PATH))
        assertTrue(probe.contains("2>&1"))
        HEALTH_NODES.forEach { assertTrue(it, probe.contains(it)) }
        assertEquals(14, HEALTH_NODES.size)
    }
}
