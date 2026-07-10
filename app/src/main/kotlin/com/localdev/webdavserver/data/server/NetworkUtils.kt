package com.localdev.webdavserver.data.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Network helpers: discovering the device's LAN IP and connectivity state.
 */
object NetworkUtils {

    /**
     * Returns the first non-loopback IPv4 address — preferring the active
     * network's link addresses, falling back to enumerating interfaces.
     */
    fun getLocalIpAddress(context: Context): String? {
        connectivityIp(context)?.let { return it }
        return runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }

    private fun connectivityIp(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val link: LinkProperties = cm.getLinkProperties(network) ?: return null
        return link.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }
}
