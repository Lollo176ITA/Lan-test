package com.lanshare.core.network

import com.lanshare.core.api.model.CapabilitySet
import com.lanshare.core.api.model.DeviceInfo
import com.lanshare.core.api.model.DeviceRole
import com.lanshare.core.api.model.DeviceStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DeviceRegistry(
    hostId: String,
    hostName: String,
    hostAddress: String,
    private val capabilities: CapabilitySet
) {
    private val devicesByToken = ConcurrentHashMap<String, DeviceInfo>()

    private val hostDevice = DeviceInfo(
        deviceId = hostId,
        deviceName = hostName,
        role = DeviceRole.HOST,
        status = DeviceStatus.ONLINE,
        ipAddress = hostAddress,
        connectedAtEpochMillis = Instant.now().toEpochMilli(),
        capabilities = capabilities
    )

    fun registerClient(deviceName: String, ipAddress: String): Pair<String, DeviceInfo> {
        val token = UUID.randomUUID().toString()
        val device = DeviceInfo(
            deviceId = UUID.randomUUID().toString(),
            deviceName = deviceName,
            role = DeviceRole.CLIENT,
            status = DeviceStatus.ONLINE,
            ipAddress = ipAddress,
            connectedAtEpochMillis = Instant.now().toEpochMilli(),
            capabilities = capabilities
        )
        devicesByToken[token] = device
        return token to device
    }

    fun isValidToken(token: String?): Boolean {
        if (token.isNullOrBlank()) {
            return false
        }
        return devicesByToken.containsKey(token)
    }

    fun deviceByToken(token: String): DeviceInfo? = devicesByToken[token]

    fun listDevices(): List<DeviceInfo> = listOf(hostDevice) + devicesByToken.values.sortedBy { it.connectedAtEpochMillis }

    fun unregister(token: String) {
        devicesByToken.remove(token)
    }
}
