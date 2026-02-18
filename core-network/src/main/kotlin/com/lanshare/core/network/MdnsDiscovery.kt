package com.lanshare.core.network

import com.lanshare.core.api.model.HostAnnouncement
import kotlinx.coroutines.delay
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

class MdnsDiscovery(
    private val serviceType: String = "_lanshare._tcp.local."
) {
    suspend fun discover(timeoutMillis: Long = 1_500): List<HostAnnouncement> {
        val announcements = mutableMapOf<String, HostAnnouncement>()
        val instance = JmDNS.create()

        val listener = object : ServiceListener {
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

                announcements[hostId] = HostAnnouncement(
                    hostId = hostId,
                    name = name,
                    apiPort = info.port,
                    version = version,
                    fingerprintShort = fingerprintShort,
                    address = info.inet4Addresses.firstOrNull()?.hostAddress
                        ?: info.inetAddresses.firstOrNull()?.hostAddress
                )
            }
        }

        return try {
            instance.addServiceListener(serviceType, listener)
            delay(timeoutMillis)
            announcements.values.sortedBy { it.name }
        } finally {
            instance.removeServiceListener(serviceType, listener)
            instance.close()
        }
    }
}
