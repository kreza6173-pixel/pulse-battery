package io.github.kreza6173pixel.pulsebattery.access

import io.github.kreza6173pixel.pulsebattery.shizuku.ShizukuUidKind
import io.github.kreza6173pixel.pulsebattery.shizuku.classifyUid

/**
 * Which privileged service is serving the binder API.
 *
 * Sui is a Magisk / KernelSU module that implements the SAME API as the Shizuku manager app,
 * so only the provider name differs: every command still goes through the user service and
 * [io.github.kreza6173pixel.pulsebattery.exec.ExecBridge].
 */
enum class ServiceProvider {
    /** No usable provider: the service is not ready. */
    NONE,

    /** The Shizuku manager app. Started over ADB (uid 2000) or as root (uid 0). */
    SHIZUKU,

    /** The Sui module. The user picks root or shell in its own permission dialog. */
    SUI,
}

/**
 * Raw, directly observable facts about privileged access. Plain values only, so the
 * resolution below stays pure and testable on the JVM.
 */
data class AccessSignals(
    /** `Sui.init(packageName)` returned true, i.e. the module is present and bound. */
    val suiActive: Boolean,
    /** `PackageManager` can resolve the Shizuku manager package. */
    val shizukuManagerInstalled: Boolean,
    /** The client reached READY: binder alive and permission granted. */
    val serviceReady: Boolean,
    /** `Shizuku.getUid()`, or -1 when not available. */
    val uid: Int,
)

/**
 * What the app can actually do right now.
 *
 * [rootAvailable] is the single gate for every root-only control. It is deliberately based on
 * the uid the service really runs with, never on "a root manager is installed somewhere": an
 * installed root manager says nothing about the privilege of this process.
 */
data class AccessMode(
    val provider: ServiceProvider,
    val uidKind: ShizukuUidKind,
) {
    val rootAvailable: Boolean get() = uidKind == ShizukuUidKind.ROOT
}

/**
 * Resolves the mode from the observed signals.
 *
 * Order matters. A service that is not ready has no provider and an unknown uid, whatever is
 * installed on the device. Sui outranks the manager app because when the module is active it
 * is the component actually answering, even if the manager app happens to be installed too.
 */
fun resolveAccessMode(signals: AccessSignals): AccessMode {
    val provider = when {
        !signals.serviceReady -> ServiceProvider.NONE
        signals.suiActive -> ServiceProvider.SUI
        signals.shizukuManagerInstalled -> ServiceProvider.SHIZUKU
        else -> ServiceProvider.NONE
    }
    val uidKind = if (signals.serviceReady) classifyUid(signals.uid) else ShizukuUidKind.UNKNOWN
    return AccessMode(provider, uidKind)
}
