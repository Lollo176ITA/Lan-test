package com.lanshare.core.network

import com.lanshare.core.api.model.HostAnnouncement
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

class MdnsDiscovery(
    private val serviceType: String = "_lanshare._tcp.local."
) {
    suspend fun discover(timeoutMillis: Long = 1_500): List<HostAnnouncement> {
        val announcements = ConcurrentHashMap<String, HostAnnouncement>()
        val instances = createInstances()
        val listeners = instances.associateWith { instance ->
            object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    instance.requestServiceInfo(event.type, event.name, true)
                }

                override fun serviceRemoved(event: ServiceEvent) = Unit

                override fun serviceResolved(event: ServiceEvent) {
                    val info = event.info ?: return
                    val hostId = info.getPropertyString("hostId") ?: return
                    val name = info.getPropertyString("name") ?: "LanShare Host"
                    val version = info.getPropertyString("version") ?: "unknown"
                    val fingerprintShort = info.getPropertyString("fingerprintShort") ?: ""
                    val txtAddress = info.getPropertyString("address")?.takeIf { it.isNotBlank() }
                    val discoveredAddress = info.inet4Addresses.firstOrNull()?.hostAddress
                        ?: info.inetAddresses.firstOrNull()?.hostAddress

                    announcements[hostId] = HostAnnouncement(
                        hostId = hostId,
                        name = name,
                        apiPort = info.port,
                        version = version,
                        fingerprintShort = fingerprintShort,
                        address = txtAddress ?: discoveredAddress
                    )
                }
            }
        }

        return try {
            listeners.forEach { (instance, listener) ->
                instance.addServiceListener(serviceType, listener)
            }
            delay(timeoutMillis)
            announcements.values.sortedBy { it.name }
        } finally {
            listeners.forEach { (instance, listener) ->
                runCatching { instance.removeServiceListener(serviceType, listener) }
            }
            instances.forEach { instance ->
                runCatching { instance.close() }
            }
        }
    }

    private fun createInstances(): List<JmDNS> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
            ?: return listOf(JmDNS.create())

        val addresses = Collections.list(interfaces)
            .asSequence()
            .filter { network ->
                runCatching { network.isUp && !network.isLoopback && !network.isVirtual && network.supportsMulticast() }
                    .getOrDefault(false)
            }
            .flatMap { network -> Collections.list(network.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { address -> !address.isLoopbackAddress }
            .distinctBy { address -> address.hostAddress }
            .mapNotNull { address ->
                runCatching { JmDNS.create(address) }.getOrNull()
            }
            .toList()

        return if (addresses.isNotEmpty()) {
            addresses
        } else {
            listOf(JmDNS.create())
        }
    }
}
