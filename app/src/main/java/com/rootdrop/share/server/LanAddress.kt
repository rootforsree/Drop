package com.rootdrop.share.server

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Finds this phone's LAN IPv4 address, or null when there is none.
 *
 * Preference order: a site-local IPv4 address (the usual 192.168.x.x /
 * 10.x.x.x / 172.16.x.x WiFi address), then any other non-loopback IPv4
 * address. Loopback (127.0.0.1) is never returned, because another phone
 * on the network cannot reach it, and no mobile-data address is picked
 * specially: on a phone connected to WiFi the site-local address is the
 * WiFi one. Interfaces that are down are skipped.
 *
 * This is deliberately a plain function over NetworkInterface, not a
 * WiFi-manager call, so it needs no extra permission beyond the network
 * state the app already uses while sharing.
 */
internal fun findLanIpAddress(): String? {
    val interfaces: List<NetworkInterface> = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
    } catch (e: Exception) {
        return null
    }
    val candidates: List<Inet4Address> = interfaces
        .filter { networkInterface: NetworkInterface ->
            try {
                networkInterface.isUp && !networkInterface.isLoopback
            } catch (e: Exception) {
                false
            }
        }
        .flatMap { networkInterface: NetworkInterface ->
            try {
                Collections.list(networkInterface.inetAddresses)
            } catch (e: Exception) {
                emptyList()
            }
        }
        .filterIsInstance<Inet4Address>()
        .filter { address: Inet4Address -> !address.isLoopbackAddress }
    val siteLocal: Inet4Address? = candidates.firstOrNull { address: Inet4Address ->
        address.isSiteLocalAddress
    }
    val chosen: Inet4Address? = siteLocal ?: candidates.firstOrNull()
    return chosen?.hostAddress
}
