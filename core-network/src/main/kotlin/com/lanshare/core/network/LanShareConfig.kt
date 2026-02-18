package com.lanshare.core.network

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Path
import java.util.Collections
import java.util.UUID

data class LanShareConfig(
    val hostId: String = UUID.randomUUID().toString(),
    val hostName: String = InetAddress.getLocalHost().hostName,
    val bindHost: String = "0.0.0.0",
    val advertisedHost: String = defaultLanAdvertisedHost(),
    val apiPort: Int = 8443,
    val appVersion: String = "0.1.0",
    val storageRoot: Path
)

private fun defaultLanAdvertisedHost(): String {
    val lanAddress = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
        Collections.list(interfaces)
            .asSequence()
            .filter {
                runCatching { it.isUp && !it.isLoopback && !it.isVirtual && it.supportsMulticast() }
                    .getOrDefault(false)
            }
            .flatMap { network -> Collections.list(network.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
    }.getOrNull()

    if (lanAddress != null) {
        return lanAddress.hostAddress
    }

    val fallback = runCatching { InetAddress.getLocalHost() }.getOrNull()
    if (fallback != null && !fallback.isLoopbackAddress && fallback.hostAddress.isNotBlank()) {
        return fallback.hostAddress
    }

    return "localhost"
}
