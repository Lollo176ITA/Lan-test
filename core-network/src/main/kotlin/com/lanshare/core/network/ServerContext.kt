package com.lanshare.core.network

import com.lanshare.core.media.LiveStreamingManager
import com.lanshare.core.media.MediaCatalog
import com.lanshare.core.media.MediaSessionCoordinator
import com.lanshare.core.sync.SyncCoordinator
import com.lanshare.core.transfer.TransferCoordinator
import kotlinx.serialization.json.Json

data class ServerContext(
    val config: LanShareConfig,
    val json: Json,
    val tlsMaterial: TlsMaterial,
    val pinManager: PinManager,
    val deviceRegistry: DeviceRegistry,
    val trustedHostStore: TrustedHostStore,
    val transferCoordinator: TransferCoordinator,
    val syncCoordinator: SyncCoordinator,
    val mediaCatalog: MediaCatalog,
    val mediaSessionCoordinator: MediaSessionCoordinator,
    val liveStreamingManager: LiveStreamingManager,
    val updateService: UpdateService,
    val eventHub: EventHub
)
