package com.lanshare.core.network

import com.lanshare.core.api.model.CapabilitySet
import com.lanshare.core.api.model.HostAnnouncement
import com.lanshare.core.media.LiveStreamingManager
import com.lanshare.core.media.MediaCatalog
import com.lanshare.core.media.MediaSessionCoordinator
import com.lanshare.core.sync.SyncCoordinator
import com.lanshare.core.transfer.TransferCoordinator
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files

class LanShareServer(
    private val config: LanShareConfig
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    private val dataRoot = config.storageRoot
    private val tlsMaterial = TlsMaterialProvider(dataRoot.resolve("security"), config.hostName).loadOrCreate()
    private val pinManager = PinManager()

    private val capabilities = CapabilitySet(
        fileTransfer = true,
        folderSync = true,
        mediaPlayback = true,
        liveStream = true,
        systemAudioCapture = true
    )

    private val deviceRegistry = DeviceRegistry(
        hostId = config.hostId,
        hostName = config.hostName,
        hostAddress = config.advertisedHost,
        capabilities = capabilities
    )

    private val trustedHostStore = TrustedHostStore(dataRoot.resolve("storage"))
    private val transferCoordinator = TransferCoordinator(dataRoot.resolve("transfers"))
    private val syncCoordinator = SyncCoordinator()
    private val mediaCatalog = MediaCatalog()
    private val mediaSessionCoordinator = MediaSessionCoordinator()
    private val liveStreamingManager = LiveStreamingManager(
        ffmpegExecutable = dataRoot.resolve("bin/ffmpeg"),
        bindHost = config.advertisedHost,
        bindPort = config.apiPort
    )
    private val updateService = UpdateService(config.appVersion)
    private val eventHub = EventHub(json)

    private val mdnsPublisher = MdnsPublisher(serviceName = "LanShare-${config.hostId.take(8)}")

    @Volatile
    private var server: ApplicationEngine? = null

    fun start(wait: Boolean = false) {
        if (server != null) {
            return
        }

        Files.createDirectories(dataRoot)

        val context = ServerContext(
            config = config,
            json = json,
            tlsMaterial = tlsMaterial,
            pinManager = pinManager,
            deviceRegistry = deviceRegistry,
            trustedHostStore = trustedHostStore,
            transferCoordinator = transferCoordinator,
            syncCoordinator = syncCoordinator,
            mediaCatalog = mediaCatalog,
            mediaSessionCoordinator = mediaSessionCoordinator,
            liveStreamingManager = liveStreamingManager,
            updateService = updateService,
            eventHub = eventHub
        )

        val environment = applicationEngineEnvironment {
            module {
                installLanShareModule(context)
            }

            sslConnector(
                keyStore = tlsMaterial.keyStore,
                keyAlias = tlsMaterial.alias,
                keyStorePassword = { tlsMaterial.keyStorePassword.toCharArray() },
                privateKeyPassword = { tlsMaterial.privateKeyPassword.toCharArray() }
            ) {
                port = this@LanShareServer.config.apiPort
                keyStorePath = tlsMaterial.keyStorePath.toFile()
            }
        }

        server = embeddedServer(Netty, environment)
        server?.start(wait = wait)

        val announcement = HostAnnouncement(
            hostId = config.hostId,
            name = config.hostName,
            apiPort = config.apiPort,
            version = config.appVersion,
            fingerprintShort = tlsMaterial.fingerprint.take(12),
            address = config.advertisedHost
        )
        runCatching {
            mdnsPublisher.publish(announcement)
        }.onFailure {
            logger.warn("Impossibile pubblicare mDNS: {}", it.message)
        }

        logger.info(
            "LanShare server avviato su https://{}:{} PIN={} fingerprint={}...",
            config.advertisedHost,
            config.apiPort,
            pinManager.currentPin(),
            tlsMaterial.fingerprint.take(12)
        )
    }

    fun stop(gracePeriodMillis: Long = 1_000, timeoutMillis: Long = 3_000) {
        server?.stop(gracePeriodMillis, timeoutMillis)
        server = null
        mdnsPublisher.close()
    }

    fun currentPin(): String = pinManager.currentPin()

    fun fingerprint(): String = tlsMaterial.fingerprint

    override fun close() {
        stop()
    }
}
