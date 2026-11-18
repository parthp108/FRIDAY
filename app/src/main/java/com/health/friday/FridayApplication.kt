package com.health.friday

import android.app.Application
import com.health.friday.ai.LocalLlmClient
import com.health.friday.di.AppContainer

class FridayApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        if (BuildConfig.DEBUG) {
            container.syncServer.startServer()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        LocalLlmClient.onTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        LocalLlmClient.onLowMemory()
    }
}
