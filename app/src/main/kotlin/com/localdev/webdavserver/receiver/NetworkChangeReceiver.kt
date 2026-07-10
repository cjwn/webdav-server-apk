package com.localdev.webdavserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import com.localdev.webdavserver.data.server.NetworkUtils
import com.localdev.webdavserver.di.AppModule

/**
 * Reacts to Wi-Fi / connectivity changes. When the network changes (e.g. SSID
 * switch), the server's reported IP and QR code are refreshed. If connectivity
 * is lost while running, the IP is cleared so the UI shows an offline state.
 *
 * Registered programmatically via [register] using a NetworkCallback for modern
 * Android; the manifest entry exists for completeness.
 */
class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppModule.init(context)
        AppModule.webDavEngine.refreshIp()
    }

    companion object {
        private var callback: ConnectivityManager.NetworkCallback? = null

        fun register(context: Context) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return
            if (callback != null) return
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    AppModule.webDavEngine.refreshIp()
                }

                override fun onLost(network: Network) {
                    AppModule.webDavEngine.refreshIp()
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching { cm.registerNetworkCallback(request, cb) }
            callback = cb
        }

        fun unregister(context: Context) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return
            callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
            callback = null
        }
    }
}
