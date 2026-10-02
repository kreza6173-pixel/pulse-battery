package io.github.kreza6173pixel.pulsebattery.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryParserTest {

    /** Transcribed from the phone: Xiaomi, Android 16 (SDK 36), 2026-10-02. */
    private val xiaomiAndroid16 = listOf(
        "Current Battery Service state:",
        "  AC powered: false",
        "  USB powered: false",
        "  Wireless powered: false",
        "  Dock powered: false",
        "  Max charging current: 0",
        " Time when the latest updated value of the Max charging current was sent via battery changed broadcast: +22s878ms",
        "  Max charging voltage: 0",
        "  Charge counter: 748",
        "  status: 3",
        "  health: 2",
        "  present: true",
        "  level: 14",
        "  scale: 100",
        "  voltage: 3678",
        " Time when the latest updated value of the voltage was sent via battery changed broadcast: +1h51m1s855ms",
        " The last voltage value sent via the battery changed broadcast: 3666",
        "  temperature: 381",
        "  technology: Li-poly",
        "  Charging state: 0",
        "  Charging policy: 0",
        "  Capacity level: 2",
        "MiuiBatteryService first usage time:",
        "  mSetBatteryUsageTimeCount=56",
        "  mNtpTime=1779945046315",
        "  mParseNtpTime=20260516",
    )

    @Test
    fun parsesRealXiaomiAndroid16Output() {
        val b = BatteryParser.parse(xiaomiAndroid16.joinToString("\n"))
        assertNotNull(b)
        b!!
        assertEquals(14, b.level)
        assertEquals(100, b.scale)
        assertEquals(14, b.percent)
        assertEquals(BatteryStatus.DISCHARGING, b.status)
        assertEquals(BatteryHealth.GOOD, b.health)
        assertFalse(b.pluggedIn)
        assertEquals(3678, b.voltageMv)
        assertEquals(381, b.temperatureTenthsC)
        assertEquals(38.1, b.temperatureC!!, 0.0001)
        assertEquals("Li-poly", b.technology)
        assertEquals(748L, b.chargeCounter)
        assertEquals(true, b.present)
    }

    @Test
    fun vendorSectionAfterTheBlockIsIgnored() {
        val withVendorNoise = xiaomiAndroid16 + listOf("VendorThing:", "  level: 99", "  voltage: 1")
        val b = BatteryParser.parse(withVendorNoise.joinToString("\n"))!!
        assertEquals(14, b.level)
        assertEquals(3678, b.voltageMv)
    }

    @Test
    fun chargingOverUsb() {
        val text = listOf(
            "Current Battery Service state:",
            "  AC powered: false",
            "  USB powered: true",
            "  status: 2",
            "  health: 2",
            "  level: 55",
            "  scale: 100",
        ).joinToString("\n")
        val b = BatteryParser.parse(text)!!
        assertEquals(BatteryStatus.CHARGING, b.status)
        assertTrue(b.usbPowered)
        assertTrue(b.pluggedIn)
        assertEquals(55, b.percent)
    }

    @Test
    fun missingHeaderGivesNull() {
        assertNull(BatteryParser.parse("Can not find service: battery"))
        assertNull(BatteryParser.parse(""))
    }

    @Test
    fun zeroScaleGivesNoPercent() {
        val text = "Current Battery Service state:\n  level: 10\n  scale: 0"
        assertNull(BatteryParser.parse(text)!!.percent)
    }
}
