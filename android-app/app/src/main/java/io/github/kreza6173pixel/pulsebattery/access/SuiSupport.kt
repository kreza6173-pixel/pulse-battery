package io.github.kreza6173pixel.pulsebattery.access

/**
 * Brings up Sui, if the device has it.
 *
 * Sui ships the same binder API as the Shizuku manager app, but it must be initialised from
 * the app process before any Shizuku call is made, otherwise the module never attaches and
 * the app wrongly concludes that no provider exists.
 *
 * Reflection is used on purpose. The entry point lives in the Sui part of the Shizuku client
 * libraries, and the exact artifact layout for the pinned version 13.1.5 was not verified on
 * this toolchain. A class that is not there must degrade to "Sui not present" at runtime
 * rather than fail the build, which is also what happens on a device without the module.
 */
object SuiSupport {

    /** True when Sui is present and attached to this process. */
    @Volatile
    var active: Boolean = false
        private set

    /** True once [init] has run, successfully or not. */
    @Volatile
    var attempted: Boolean = false
        private set

    /** Why the attempt ended the way it did. Shown in the console bind log, never parsed. */
    @Volatile
    var note: String = "not attempted"
        private set

    /**
     * Initialises Sui at most once per process. Safe to call when the module is absent.
     * Must run before anything touches the Shizuku API.
     */
    fun init(packageName: String) {
        if (attempted) return
        attempted = true
        runCatching {
            val suiClass = Class.forName(SUI_CLASS)
            val initMethod = suiClass.getDeclaredMethod("init", String::class.java)
            initMethod.invoke(null, packageName) as? Boolean ?: false
        }.fold(
            onSuccess = { ok ->
                active = ok
                note = if (ok) "Sui attached" else "Sui present but did not attach"
            },
            onFailure = { error ->
                active = false
                note = "Sui not available (" + error.javaClass.simpleName + ")"
            },
        )
    }

    private const val SUI_CLASS = "rikka.sui.Sui"
}
