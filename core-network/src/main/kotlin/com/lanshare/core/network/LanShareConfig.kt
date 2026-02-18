package com.lanshare.core.network

import java.net.InetAddress
import java.nio.file.Path
import java.util.UUID

data class LanShareConfig(
    val hostId: String = UUID.randomUUID().toString(),
    val hostName: String = InetAddress.getLocalHost().hostName,
    val bindHost: String = "0.0.0.0",
    val advertisedHost: String = "localhost",
    val apiPort: Int = 8443,
    val appVersion: String = "0.1.0",
    val storageRoot: Path
)
