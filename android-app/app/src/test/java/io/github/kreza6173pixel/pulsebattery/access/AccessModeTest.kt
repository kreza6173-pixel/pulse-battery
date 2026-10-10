package io.github.kreza6173pixel.pulsebattery.access

import io.github.kreza6173pixel.pulsebattery.shizuku.ShizukuUidKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessModeTest {

    private fun signals(
        sui: Boolean = false,
        manager: Boolean = false,
        ready: Boolean = false,
        uid: Int = -1,
    ) = AccessSignals(sui, manager, ready, uid)

    @Test
    fun serviceNotReadyHasNoProviderAndUnknownUid() {
        val mode = resolveAccessMode(signals(sui = true, manager = true, ready = false, uid = 0))
        assertEquals(ServiceProvider.NONE, mode.provider)
        assertEquals(ShizukuUidKind.UNKNOWN, mode.uidKind)
        assertFalse(mode.rootAvailable)
    }

    @Test
    fun suiOutranksTheManagerApp() {
        val mode = resolveAccessMode(signals(sui = true, manager = true, ready = true, uid = 0))
        assertEquals(ServiceProvider.SUI, mode.provider)
        assertEquals(ShizukuUidKind.ROOT, mode.uidKind)
        assertTrue(mode.rootAvailable)
    }

    @Test
    fun suiCanAlsoServeAsShell() {
        val mode = resolveAccessMode(signals(sui = true, ready = true, uid = 2000))
        assertEquals(ServiceProvider.SUI, mode.provider)
        assertEquals(ShizukuUidKind.SHELL, mode.uidKind)
        assertFalse(mode.rootAvailable)
    }

    @Test
    fun managerAppOverAdbIsShell() {
        val mode = resolveAccessMode(signals(manager = true, ready = true, uid = 2000))
        assertEquals(ServiceProvider.SHIZUKU, mode.provider)
        assertFalse(mode.rootAvailable)
    }

    @Test
    fun managerAppStartedAsRootUnlocksRoot() {
        val mode = resolveAccessMode(signals(manager = true, ready = true, uid = 0))
        assertEquals(ServiceProvider.SHIZUKU, mode.provider)
        assertTrue(mode.rootAvailable)
    }

    @Test
    fun anUnexpectedUidNeverUnlocksRoot() {
        val mode = resolveAccessMode(signals(manager = true, ready = true, uid = 10123))
        assertEquals(ShizukuUidKind.OTHER, mode.uidKind)
        assertFalse(mode.rootAvailable)
    }

    @Test
    fun readyWithoutAnyProviderIsStillNone() {
        val mode = resolveAccessMode(signals(ready = true, uid = 2000))
        assertEquals(ServiceProvider.NONE, mode.provider)
    }
}
