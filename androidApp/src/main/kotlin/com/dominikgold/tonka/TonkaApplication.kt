package com.dominikgold.tonka

import android.app.Application
import com.dominikgold.tonka.platform.PlatformContext

class TonkaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startTonka(PlatformContext(this))
    }
}
