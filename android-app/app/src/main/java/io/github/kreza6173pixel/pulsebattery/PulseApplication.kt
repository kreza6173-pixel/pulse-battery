package io.github.kreza6173pixel.pulsebattery

import android.app.Application
import io.github.kreza6173pixel.pulsebattery.access.SuiSupport

/**
 * Exists for one reason: Sui has to be initialised before the first Shizuku call, and the
 * application object is the earliest reliable place to do it. Everything else stays where it
 * was, and no state is kept here.
 */
class PulseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SuiSupport.init(packageName)
    }
}
