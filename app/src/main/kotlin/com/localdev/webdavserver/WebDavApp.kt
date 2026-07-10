package com.localdev.webdavserver

import android.app.Application
import com.localdev.webdavserver.di.AppModule
import com.localdev.webdavserver.receiver.NetworkChangeReceiver

/**
 * Application entry point — initializes the manual DI container and registers a
 * network callback for live IP/QR updates.
 */
class WebDavApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
        NetworkChangeReceiver.register(this)
    }
}
