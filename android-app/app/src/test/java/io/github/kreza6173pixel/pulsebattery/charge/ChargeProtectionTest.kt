package io.github.kreza6173pixel.pulsebattery.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeProtectionTest {

    private val realProbe = """
        key=2
        mios=OS3.0.302.0.WOGMIXM
        miosname=
        miui=
    """.trimIndent()

    private val factoryProbe = """
        key=null
        mios=OS3.0.302.0.WOGMIXM
        miosname=
        miui=
    """.trimIndent()

    private val foreignProbe = """
        key=null
        mios=
        miosname=
        miui=
    """.trimIndent()

    @Test
    fun labelledLinesAreParsedRegardlessOfOrder() {
        val values = ChargeParsers.parseLabelled("miui=\nkey=0\nmios=OS3")
        assertEquals("0", values[LABEL_KEY])
        assertEquals("OS3", values[LABEL_MI_OS])
        assertEquals("", values[LABEL_MIUI])
    }

    @Test
    fun linesWithoutASeparatorAreIgnored() {
        val values = ChargeParsers.parseLabelled("garbage\n=orphan\nkey=1")
        assertEquals(1, values.size)
        assertEquals("1", values[LABEL_KEY])
    }

    @Test
    fun theWordNullAndEmptyBothMeanAbsent() {
        assertTrue(ChargeParsers.isAbsent("null"))
        assertTrue(ChargeParsers.isAbsent(" "))
        assertTrue(ChargeParsers.isAbsent(null))
        assertFalse(ChargeParsers.isAbsent("0"))
    }

    @Test
    fun modeValueIgnoresAbsentAndNonNumeric() {
        assertEquals(2, ChargeParsers.modeValueOf("2"))
        assertEquals(0, ChargeParsers.modeValueOf(" 0 "))
        assertNull(ChargeParsers.modeValueOf("null"))
        assertNull(ChargeParsers.modeValueOf("on"))
    }

    @Test
    fun theFirstNonEmptyMarkerWins() {
        val values = ChargeParsers.parseLabelled(realProbe)
        assertEquals("OS3.0.302.0.WOGMIXM", ChargeParsers.romMarkerOf(values))
    }

    @Test
    fun noMarkerAtAllGivesAnEmptyString() {
        val values = ChargeParsers.parseLabelled(foreignProbe)
        assertEquals("", ChargeParsers.romMarkerOf(values))
    }

    @Test
    fun aKnownValueMeansSupported() {
        val values = ChargeParsers.parseLabelled(realProbe)
        val state = resolveChargeState(values[LABEL_KEY], ChargeParsers.romMarkerOf(values))
        assertEquals(ChargeSupport.SUPPORTED, state.support)
        assertEquals(ChargeMode.PROTECTED, state.mode)
    }

    @Test
    fun anAbsentKeyOnAXiaomiRomIsTheFactoryDefault() {
        val values = ChargeParsers.parseLabelled(factoryProbe)
        val state = resolveChargeState(values[LABEL_KEY], ChargeParsers.romMarkerOf(values))
        assertEquals(ChargeSupport.FACTORY_DEFAULT, state.support)
        assertNull(state.mode)
    }

    @Test
    fun anotherRomIsUnsupported() {
        val values = ChargeParsers.parseLabelled(foreignProbe)
        val state = resolveChargeState(values[LABEL_KEY], ChargeParsers.romMarkerOf(values))
        assertEquals(ChargeSupport.UNSUPPORTED, state.support)
    }

    @Test
    fun anUnknownNumberStillCountsAsSupportedButHasNoMode() {
        val state = resolveChargeState("7", "OS3")
        assertEquals(ChargeSupport.SUPPORTED, state.support)
        assertNull(state.mode)
        assertEquals("7", state.rawKeyValue)
    }

    @Test
    fun everyModeMapsBothWays() {
        assertEquals(ChargeMode.CHARGE_FULLY, ChargeMode.fromKeyValue(0))
        assertEquals(ChargeMode.INTELLIGENT, ChargeMode.fromKeyValue(1))
        assertEquals(ChargeMode.PROTECTED, ChargeMode.fromKeyValue(2))
        assertNull(ChargeMode.fromKeyValue(null))
        assertEquals(0, ChargeMode.CHARGE_FULLY.keyValue)
        assertEquals(1, ChargeMode.INTELLIGENT.keyValue)
        assertEquals(2, ChargeMode.PROTECTED.keyValue)
    }

    @Test
    fun theProbeAsksForTheKeyAndTheMarkers() {
        val probe = ChargeRepository.PROBE_COMMAND
        assertTrue(probe.contains(CHARGE_MODE_SETTING))
        assertTrue(probe.contains("$LABEL_KEY="))
        assertTrue(probe.contains("ro.mi.os.version.incremental"))
    }

    @Test
    fun theSettingNameIsQuotedInTheGetCommand() {
        assertEquals(
            "settings get secure 'security_pc_secure_protect_mode_key'",
            ChargeRepository.GET_COMMAND,
        )
    }

    @Test
    fun theSettingNameIsAFixedConstant() {
        assertEquals("security_pc_secure_protect_mode_key", CHARGE_MODE_SETTING)
    }

    @Test
    fun rawValuesAreKeptForDisplay() {
        val state = resolveChargeState(" 2 ", "OS3.0.302.0.WOGMIXM")
        assertEquals("2", state.rawKeyValue)
        assertEquals("OS3.0.302.0.WOGMIXM", state.romMarker)
    }
}
