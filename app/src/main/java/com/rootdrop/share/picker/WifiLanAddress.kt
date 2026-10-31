package com.rootdrop.share.picker

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections
import java.util.Locale

/**
 * Returns the phone's current WiFi LAN IPv4 address, or null when there is
 * none (for example when WiFi is off or not connected).
 *
 * Selection rule, in order:
 * 1. The interface must be up, must not be a loopback interface, and its
 *    name must start with "wlan", which is how Android names WiFi
 *    interfaces. This is what keeps mobile-data interfaces out: when no
 *    WiFi interface matches, this function returns null instead of falling
 *    back to any other interface.
 * 2. The address must be IPv4, must not be a loopback address, and must
 *    not be link-local, so 127.0.0.1 and unusable self-assigned addresses
 *    are never returned.
 * 3. Among matching addresses, a site-local LAN address is preferred;
 *    otherwise the first matching address is used.
 */
fun currentWifiLanIpv4(): String? {
    return try {
        val interfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces()?.let { enumeration ->
            Collections.list(enumeration)
        } ?: emptyList()
        val wifiInterfaces: List<NetworkInterface> = interfaces.filter { networkInterface ->
            networkInterface.isUp &&
                !networkInterface.isLoopback &&
                networkInterface.name.lowercase(Locale.ROOT).startsWith("wlan")
        }
        val addresses: List<InetAddress> = wifiInterfaces.flatMap { networkInterface ->
            Collections.list(networkInterface.inetAddresses)
        }
        val candidates: List<InetAddress> = addresses.filter { address ->
            address is Inet4Address &&
                !address.isLoopbackAddress &&
                !address.isLinkLocalAddress
        }
        val preferred: InetAddress? = candidates.firstOrNull { address ->
            address.isSiteLocalAddress
        } ?: candidates.firstOrNull()
        preferred?.hostAddress
    } catch (e: SocketException) {
        null
    }
}
