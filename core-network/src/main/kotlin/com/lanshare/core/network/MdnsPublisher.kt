package com.lanshare.core.network

import com.lanshare.core.api.model.HostAnnouncement
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class MdnsPublisher(
    private val serviceType: String = "_lanshare._tcp.local.",
    private val serviceName: String,
    private val bindAddress: String? = null
) : AutoCloseable {
    private val instances = mutableListOf<JmDNS>()
    private val serviceInfos = mutableListOf<ServiceInfo>()

    fun publish(announcement: HostAnnouncement) {
        close()
        val addresses = resolveBindAddresses()
        if (addresses.isEmpty()) {
            registerOn(address = null, announcement = announcement)
            return
        }

        addresses.forEach { address ->
            registerOn(address = address, announcement = announcement)
        }
    }

    override fun close() {
        instances.zip(serviceInfos).forEach { (instance, info) ->
            runCatching { instance.unregisterService(info) }
        }
        serviceInfos.clear()
        instances.forEach { instance ->
            runCatching { instance.close() }
        }
        instances.clear()
    }

    private fun resolveBindAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()

        val configured = bindAddress?.takeIf { it.isNotBlank() }
        if (configured != null) {
            val explicitAddress = runCatching { InetAddress.getByName(configured) }.getOrNull()
            if (explicitAddress != null && !explicitAddress.isLoopbackAddress) {
                addresses += explicitAddress
            }
        }

        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
        if (interfaces != null) {
            val interfaceAddresses = Collections.list(interfaces)
                .asSequence()
                .filter { network ->
                    runCatching { network.isUp && !network.isLoopback && !network.isVirtual && network.supportsMulticast() }
                        .getOrDefault(false)
                }
                .flatMap { network -> Collections.list(network.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { address -> !address.isLoopbackAddress }
                .toList()
            addresses += interfaceAddresses
        }

        return addresses
            .distinctBy { it.hostAddress }
            .filter { it.hostAddress.isNotBlank() }
    }

    private fun registerOn(address: InetAddress?, announcement: HostAnnouncement) {
        val instance = if (address != null) {
            JmDNS.create(address)
        } else {
            JmDNS.create()
        }
        val info = ServiceInfo.create(
            serviceType,
            serviceName,
            announcement.apiPort,
            0,
            0,
            mapOf(
                "hostId" to announcement.hostId,
                "name" to announcement.name,
                "apiPort" to announcement.apiPort.toString(),
                "version" to announcement.version,
                "fingerprintShort" to announcement.fingerprintShort,
                "address" to (announcement.address ?: "")
            )
        )

        instance.registerService(info)
        instances += instance
        serviceInfos += info
    }
}
