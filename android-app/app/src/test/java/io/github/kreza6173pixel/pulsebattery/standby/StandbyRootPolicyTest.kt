package io.github.kreza6173pixel.pulsebattery.standby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyRootPolicyTest {

    private fun row(
        pkg: String = "com.example.app",
        code: Int? = StandbyBucket.EXEMPTED.code,
        whitelist: Set<String> = emptySet(),
    ) = AppStandbyRow(pkg, code, whitelist)

    @Test
    fun plainRowHasNoExemptSource() {
        val r = row(code = StandbyBucket.WORKING_SET.code)
        assertEquals(ExemptSource.NONE, StandbyRootPolicy.exemptSource(r))
    }

    @Test
    fun userWhitelistIsReportedAndRemovable() {
        val r = row(whitelist = setOf("user"))
        assertEquals(ExemptSource.USER_WHITELIST, StandbyRootPolicy.exemptSource(r))
        val plan = StandbyRootPolicy.plan(r, StandbyBucket.RESTRICTED, rootAvailable = true)
        assertTrue(plan.allowed)
        assertTrue(plan.dropUserWhitelist)
    }

    @Test
    fun romWhitelistOutranksUserWhitelist() {
        val r = row(whitelist = setOf("user", "system-excidle"))
        assertEquals(ExemptSource.SYSTEM_WHITELIST, StandbyRootPolicy.exemptSource(r))
        val plan = StandbyRootPolicy.plan(r, StandbyBucket.RARE, rootAvailable = true)
        // Still attempted, but the user entry is dropped and the ROM entry is disclosed.
        assertTrue(plan.allowed)
        assertTrue(plan.dropUserWhitelist)
        assertEquals(StandbyRootPolicy.REASON_SYSTEM_WHITELIST, plan.reason)
    }

    @Test
    fun exemptedWithoutWhitelistIsFrameworkHeld() {
        assertEquals(ExemptSource.FRAMEWORK, StandbyRootPolicy.exemptSource(row()))
        val plan = StandbyRootPolicy.plan(row(), StandbyBucket.RARE, rootAvailable = true)
        assertTrue(plan.allowed)
        assertFalse(plan.dropUserWhitelist)
        assertEquals(StandbyRootPolicy.REASON_FRAMEWORK, plan.reason)
    }

    @Test
    fun neverIsFrameworkHeldToo() {
        val r = row(code = StandbyBucket.NEVER.code)
        assertEquals(ExemptSource.FRAMEWORK, StandbyRootPolicy.exemptSource(r))
    }

    @Test
    fun withoutRootNothingIsAllowed() {
        val plan = StandbyRootPolicy.plan(row(), StandbyBucket.RARE, rootAvailable = false)
        assertFalse(plan.allowed)
        assertEquals(StandbyRootPolicy.REASON_NO_ROOT, plan.reason)
    }

    @Test
    fun derivedBucketsAreNeverTargets() {
        for (target in listOf(StandbyBucket.EXEMPTED, StandbyBucket.NEVER)) {
            val plan = StandbyRootPolicy.plan(row(), target, rootAvailable = true)
            assertFalse(plan.allowed)
            assertEquals(StandbyRootPolicy.REASON_DERIVED_TARGET, plan.reason)
        }
    }

    @Test
    fun invalidPackageIsRefusedBeforeAnythingElse() {
        val plan = StandbyRootPolicy.plan(
            row(pkg = "not a package; rm -rf /"),
            StandbyBucket.RARE,
            rootAvailable = true,
        )
        assertFalse(plan.allowed)
        assertEquals(StandbyRootPolicy.REASON_BAD_PACKAGE, plan.reason)
    }
}
