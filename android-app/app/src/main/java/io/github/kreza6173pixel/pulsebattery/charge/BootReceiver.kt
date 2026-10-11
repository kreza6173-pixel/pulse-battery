package io.github.kreza6173pixel.pulsebattery.charge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings the charge limit back after a reboot, and only then.
 *
 * This receiver exists for one reason: the charge gate survives a reboot on some kernels, so a
 * phone that rebooted while charging was held off would stay plugged in without charging. The
 * service opens the gate again on its first poll.
 *
 * It does nothing at all unless the user turned the limit on or asked for the limit to be
 * restored after a reboot, so the app does not become an always-on component by installing it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = ChargeLimitStore(context).read()
        if (!settings.enabled && !settings.restoreOnBoot) return
        ChargeLimitService.start(context)
    }
}
