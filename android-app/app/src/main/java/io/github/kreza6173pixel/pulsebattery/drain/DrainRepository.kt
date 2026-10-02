package io.github.kreza6173pixel.pulsebattery.drain

import io.github.kreza6173pixel.pulsebattery.diag.AlarmParser
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.exec.ExecOutcome
import io.github.kreza6173pixel.pulsebattery.exec.ShellQuoting
import io.github.kreza6173pixel.pulsebattery.standby.ActionResult
import io.github.kreza6173pixel.pulsebattery.standby.StandbyBucket
import io.github.kreza6173pixel.pulsebattery.standby.StandbyParsers
import io.github.kreza6173pixel.pulsebattery.standby.StandbyRepository

/**
 * Drain report commands. Every call blocks on the UserService: call from Dispatchers.IO.
 * Writes go through StandbyRepository, so they are validated and read back.
 */
class DrainRepository(private val bridge: ExecBridge) {

    private val standby = StandbyRepository(bridge)

    /** grep exits 1 when nothing matches: a valid empty map, not an error. */
    fun readTotals(): DiagResult<Map<String, Int>> =
        when (val o = bridge.execBlocking(TOTALS_COMMAND, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> DiagResult.Error(o.message, "")
            is ExecOutcome.Completed -> {
                val r = o.result
                val raw = if (r.stderr.isEmpty()) r.stdout else r.stdout + "\n" + r.stderr
                if (r.exitCode != 0 && r.exitCode != 1) {
                    DiagResult.Error("exit ${r.exitCode}", raw)
                } else {
                    DiagResult.Ok(AlarmParser.parseAlarmStatsTotals(r.stdout), raw)
                }
            }
        }

    /** Bucket codes for [pkgs]; one bulk read, then single reads for whatever it missed. */
    fun readBuckets(pkgs: Collection<String>): Map<String, Int> {
        val all: Map<String, Int> =
            when (val o = bridge.execBlocking(StandbyRepository.BUCKETS_COMMAND, TIMEOUT_MS)) {
                is ExecOutcome.Failed -> emptyMap()
                is ExecOutcome.Completed -> StandbyParsers.parseBucketList(o.result.stdout)
            }
        val out = LinkedHashMap<String, Int>()
        for (p in pkgs) {
            val code = all[p] ?: singleBucket(p) ?: continue
            out[p] = code
        }
        return out
    }

    fun restrict(pkg: String): ActionResult = standby.setBucket(pkg, StandbyBucket.RESTRICTED)

    fun revert(pkg: String, to: StandbyBucket): ActionResult = standby.setBucket(pkg, to)

    private fun singleBucket(pkg: String): Int? {
        if (!StandbyParsers.isValidPackage(pkg)) return null
        val command = "am get-standby-bucket " + ShellQuoting.quote(pkg)
        return when (val o = bridge.execBlocking(command, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> null
            is ExecOutcome.Completed -> StandbyParsers.parseSingleBucket(o.result.stdout)
        }
    }

    companion object {
        /** Only the per-package Alarm Stats headers cross the binder, far below the output cap. */
        const val TOTALS_COMMAND = "dumpsys alarm | grep -E \"running, [0-9]+ wakeups:\""
        private const val TIMEOUT_MS = 15_000

        private val RESTRICTABLE = setOf(
            StandbyBucket.ACTIVE,
            StandbyBucket.WORKING_SET,
            StandbyBucket.FREQUENT,
            StandbyBucket.RARE,
        )

        /** Never our own app, never exempted / never / already restricted, never an odd name. */
        fun canRestrict(pkg: String, code: Int?, ownPkg: String): Boolean {
            if (pkg == ownPkg || !StandbyParsers.isValidPackage(pkg)) return false
            val b = StandbyBucket.fromCode(code) ?: return false
            return b in RESTRICTABLE
        }
    }
}
