package com.lanshare.core.network

import com.lanshare.core.api.model.HostAnnouncement
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class MdnsPublisher(
    private val serviceType: String = "_lanshare._tcp.local.",
    private val serviceName: String,
    private val bindAddress: String? = null
) : AutoCloseable {
    private var jmdns: JmDNS? = null
    private var serviceInfo: ServiceInfo? = null

    fun publish(announcement: HostAnnouncement) {
        close()
        val instance = createInstance()
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
        jmdns = instance
        serviceInfo = info
    }

    override fun close() {
        serviceInfo?.let { info ->
            jmdns?.unregisterService(info)
        }
        serviceInfo = null
        jmdns?.close()
        jmdns = null
    }

    private fun createInstance(): JmDNS {
        val configured = bindAddress?.takeIf { it.isNotBlank() }
        if (configured != null) {
            val explicitAddress = runCatching { InetAddress.getByName(configured) }.getOrNull()
            if (explicitAddress != null && !explicitAddress.isLoopbackAddress) {
                return JmDNS.create(explicitAddress)
            }
        }
        return JmDNS.create()
    }
}
