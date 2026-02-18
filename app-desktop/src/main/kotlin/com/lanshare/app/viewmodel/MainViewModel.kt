package com.lanshare.app.viewmodel

import com.lanshare.core.api.model.CreateTransferRequest
import com.lanshare.core.api.model.DeviceInfo
import com.lanshare.core.api.model.HostAnnouncement
import com.lanshare.core.api.model.JoinRequest
import com.lanshare.core.api.model.LiveStartRequest
import com.lanshare.core.api.model.PlaybackMode
import com.lanshare.core.api.model.SyncPairRequest
import com.lanshare.core.api.model.SyncScanRequest
import com.lanshare.core.network.LanShareClient
import com.lanshare.core.network.LanShareConfig
import com.lanshare.core.network.LanShareServer
import com.lanshare.core.network.MdnsDiscovery
import com.lanshare.core.network.TrustedHostStore
import com.lanshare.core.transfer.Hashing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class MainViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appDataDir = Path.of(System.getProperty("user.home"), ".lanshare")
    private val trustedHostStore = TrustedHostStore(appDataDir.resolve("client"))

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var server: LanShareServer? = null
    private var client: LanShareClient? = null
    private var sessionToken: String? = null
    private var discoveryJob: Job? = null
    private var devicePollingJob: Job? = null

    @Volatile
    private var uploadInProgress: Boolean = false

    fun startHost() {
        scope.launch {
            if (server != null) {
                appendLog("Host gia avviato")
                return@launch
            }

            Files.createDirectories(appDataDir)
            val localConfig = LanShareConfig(
                hostName = "LanShare-${UUID.randomUUID().toString().take(4)}",
                storageRoot = appDataDir.resolve("host")
            )
            val localServer = LanShareServer(
                localConfig
            )

            localServer.start(wait = false)
            server = localServer

            _uiState.update {
                it.copy(
                    hostRunning = true,
                    hostFingerprint = localServer.fingerprint().take(16),
                    localHostId = localConfig.hostId,
                    hostEndpoint = "https://${localConfig.advertisedHost}:${localConfig.apiPort}"
                )
            }
            appendLog("Host avviato su ${localConfig.advertisedHost}:${localConfig.apiPort}")
        }
    }

    fun stopHost() {
        scope.launch {
            server?.stop()
            server = null
            _uiState.update {
                it.copy(
                    hostRunning = false,
                    hostFingerprint = "",
                    localHostId = "",
                    hostEndpoint = ""
                )
            }
            appendLog("Host fermato")
        }
    }

    fun discoverHosts() {
        scope.launch {
            discoverHostsInternal(showLog = true)
        }
    }

    fun setAutoDiscovery(enabled: Boolean) {
        if (enabled) {
            if (discoveryJob?.isActive == true) {
                return
            }
            _uiState.update { it.copy(autoDiscoveryEnabled = true) }
            discoveryJob = scope.launch {
                while (isActive) {
                    discoverHostsInternal(showLog = false)
                    delay(5_000)
                }
            }
            appendLog("Auto-discovery attivata")
        } else {
            discoveryJob?.cancel()
            discoveryJob = null
            _uiState.update { it.copy(autoDiscoveryEnabled = false) }
            appendLog("Auto-discovery disattivata")
        }
    }

    fun connectToHost(baseUrl: String, hostId: String, deviceName: String) {
        scope.launch {
            if (hostId.isBlank()) {
                _uiState.update {
                    it.copy(blockingWarning = "Inserisci hostId prima di connetterti")
                }
                return@launch
            }

            try {
                devicePollingJob?.cancel()
                devicePollingJob = null
                client?.close()

                val newClient = LanShareClient(baseUrl)
                val join = newClient.join(
                    JoinRequest(
                        hostId = hostId,
                        deviceName = deviceName.ifBlank { "Desktop Client" },
                        devicePublicKey = "not-used-yet"
                    )
                )

                val trusted = trustedHostStore.get(hostId)
                if (trusted != null && trusted.fingerprint != join.hostFingerprint) {
                    _uiState.update {
                        it.copy(
                            blockingWarning = "Fingerprint host cambiata! Connessione bloccata.",
                            connected = false,
                            sessionToken = ""
                        )
                    }
                    appendLog("Connessione bloccata: fingerprint mismatch (TOFU)")
                    newClient.close()
                    return@launch
                }

                trustedHostStore.save(hostId, join.hostFingerprint)
                client = newClient
                sessionToken = join.sessionToken

                _uiState.update {
                    it.copy(
                        connected = true,
                        sessionToken = join.sessionToken,
                        blockingWarning = null,
                        connectedHostId = hostId,
                        connectedBaseUrl = baseUrl,
                        connectedDevices = emptyList()
                    )
                }
                appendLog("Connesso all'host $hostId")
                refreshConnectedDevices()
                startDevicePolling()
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        connected = false,
                        sessionToken = "",
                        blockingWarning = "Join fallita: ${exception.message}"
                    )
                }
                appendLog("Join fallita: ${exception.message}")
            }
        }
    }

    fun quickConnect(host: HostAnnouncement, deviceName: String) {
        val hostAddress = host.address ?: "localhost"
        val baseUrl = "https://$hostAddress:${host.apiPort}"
        connectToHost(baseUrl, host.hostId, deviceName)
    }

    fun disconnectClient() {
        scope.launch {
            devicePollingJob?.cancel()
            devicePollingJob = null
            client?.close()
            client = null
            sessionToken = null
            _uiState.update {
                it.copy(
                    connected = false,
                    sessionToken = "",
                    connectedHostId = "",
                    connectedBaseUrl = "",
                    connectedDevices = emptyList()
                )
            }
            appendLog("Client disconnesso")
        }
    }

    fun refreshConnectedDevices() {
        scope.launch {
            refreshConnectedDevicesInternal(showLogOnError = true)
        }
    }

    fun createSyncPair(hostPath: String, clientPath: String, clientDeviceId: String) {
        scope.launch {
            val api = client
            val token = sessionToken
            if (api == null || token.isNullOrBlank()) {
                appendLog("Sync pair non creata: client non connesso")
                return@launch
            }
            if (hostPath.isBlank() || clientPath.isBlank() || clientDeviceId.isBlank()) {
                appendLog("Sync pair non creata: campi mancanti")
                return@launch
            }

            runCatching {
                api.createSyncPair(
                    token,
                    SyncPairRequest(
                        hostPath = hostPath,
                        clientDeviceId = clientDeviceId,
                        clientPath = clientPath
                    )
                )
            }.onSuccess { pair ->
                _uiState.update {
                    it.copy(lastSyncPairId = pair.pairId)
                }
                appendLog("Sync pair creata: ${pair.pairId.take(8)}...")
            }.onFailure { error ->
                appendLog("Errore creazione sync pair: ${error.message}")
            }
        }
    }

    fun runSyncScan(pairId: String?) {
        scope.launch {
            val api = client
            val token = sessionToken
            if (api == null || token.isNullOrBlank()) {
                appendLog("Sync scan non eseguita: client non connesso")
                return@launch
            }

            runCatching {
                api.syncScan(token, SyncScanRequest(pairId = pairId?.ifBlank { null }))
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        lastSyncDeltaCount = result.deltas.size,
                        lastSyncConflictCount = result.conflicts.size,
                        syncConflictItems = result.conflicts.map { conflict ->
                            "${conflict.relativePath} -> ${conflict.conflictCopyName}"
                        }
                    )
                }
                appendLog("Sync scan completata: delta=${result.deltas.size}, conflitti=${result.conflicts.size}")
            }.onFailure { error ->
                appendLog("Errore sync scan: ${error.message}")
            }
        }
    }

    fun registerMedia(path: String) {
        scope.launch {
            val api = client
            val token = sessionToken
            if (api == null || token.isNullOrBlank()) {
                appendLog("Media non registrato: client non connesso")
                return@launch
            }
            if (path.isBlank()) {
                appendLog("Media non registrato: percorso mancante")
                return@launch
            }

            runCatching {
                api.registerMedia(token, path)
            }.onSuccess { media ->
                _uiState.update {
                    it.copy(
                        lastRegisteredMediaId = media.mediaId,
                        lastRegisteredMediaPath = media.path
                    )
                }
                appendLog("Media registrato: ${media.mediaId.take(8)}...")
            }.onFailure { error ->
                appendLog("Errore registrazione media: ${error.message}")
            }
        }
    }

    fun startMediaSession(mode: PlaybackMode) {
        scope.launch {
            val api = client
            val token = sessionToken
            val mediaId = _uiState.value.lastRegisteredMediaId
            if (api == null || token.isNullOrBlank()) {
                appendLog("Sessione media non avviata: client non connesso")
                return@launch
            }
            if (mediaId.isBlank()) {
                appendLog("Sessione media non avviata: registra prima un media")
                return@launch
            }

            val hostDeviceId = _uiState.value.connectedHostId.ifBlank { "host" }
            runCatching {
                api.createMediaSession(
                    token,
                    com.lanshare.core.api.model.MediaSessionRequest(
                        mediaId = mediaId,
                        mode = mode,
                        hostDeviceId = hostDeviceId
                    )
                )
            }.onSuccess { session ->
                _uiState.update {
                    it.copy(
                        activeMediaSessionId = session.sessionId,
                        activeMediaMode = session.mode.name
                    )
                }
                appendLog("Sessione media avviata: ${session.sessionId.take(8)}...")
            }.onFailure { error ->
                appendLog("Errore avvio sessione media: ${error.message}")
            }
        }
    }

    fun startLive(includeSystemAudio: Boolean) {
        scope.launch {
            val api = client
            val token = sessionToken
            if (api == null || token.isNullOrBlank()) {
                appendLog("Live non avviata: client non connesso")
                return@launch
            }

            val hostDeviceId = _uiState.value.connectedHostId.ifBlank { "host" }
            runCatching {
                api.startLive(
                    token,
                    LiveStartRequest(
                        hostDeviceId = hostDeviceId,
                        includeSystemAudio = includeSystemAudio,
                        targetWidth = 1280,
                        targetHeight = 720,
                        targetFps = 30,
                        maxViewers = 4
                    )
                )
            }.onSuccess { state ->
                _uiState.update {
                    it.copy(
                        liveSessionId = state.sessionId,
                        liveStreamUrl = state.streamUrl ?: "",
                        liveStatus = state.status.name
                    )
                }
                appendLog("Live avviata: stato=${state.status.name}")
            }.onFailure { error ->
                appendLog("Errore avvio live: ${error.message}")
            }
        }
    }

    fun stopLive() {
        scope.launch {
            val api = client
            val token = sessionToken
            val sessionId = _uiState.value.liveSessionId
            if (api == null || token.isNullOrBlank() || sessionId.isBlank()) {
                appendLog("Stop live ignorato: dati mancanti")
                return@launch
            }

            runCatching {
                api.stopLive(token, sessionId)
            }.onSuccess { state ->
                _uiState.update {
                    it.copy(
                        liveStatus = state.status.name,
                        liveStreamUrl = ""
                    )
                }
                appendLog("Live fermata: stato=${state.status.name}")
            }.onFailure { error ->
                appendLog("Errore stop live: ${error.message}")
            }
        }
    }

    fun onFilesDropped(paths: List<Path>) {
        if (paths.isEmpty()) {
            return
        }

        val files = expandDroppedPaths(paths)
        if (files.isEmpty()) {
            appendLog("Nessun file valido trovato nel drop")
            return
        }

        _uiState.update { state ->
            val newItems = files.map { path ->
                TransferQueueItem(
                    localId = UUID.randomUUID().toString(),
                    fileName = path.fileName.toString(),
                    absolutePath = path.toAbsolutePath().toString(),
                    sizeBytes = runCatching { Files.size(path) }.getOrDefault(0),
                    progress = 0.0,
                    status = "In coda"
                )
            }
            state.copy(transferQueue = state.transferQueue + newItems)
        }

        appendLog("Aggiunti ${files.size} file alla coda")

        if (_uiState.value.connected) {
            uploadQueuedFiles()
        }
    }

    fun uploadQueuedFiles() {
        scope.launch {
            uploadQueuedFilesInternal()
        }
    }

    private suspend fun uploadQueuedFilesInternal() {
        if (uploadInProgress) {
            appendLog("Upload gia in corso")
            return
        }
        uploadInProgress = true
        _uiState.update { it.copy(uploadInProgress = true) }
        try {
            val api = client
            val token = sessionToken
            if (api == null || token.isNullOrBlank()) {
                appendLog("Upload rimandato: client non connesso")
                return
            }

            val queueSnapshot = _uiState.value.transferQueue.filter {
                it.status != "Completato" && it.status != "Uploading" && it.status != "Hashing"
            }
            for (item in queueSnapshot) {
                val path = Path.of(item.absolutePath)
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    updateItemStatus(item.localId, "Saltato (non file)")
                    continue
                }

                try {
                    updateItemStatus(item.localId, "Hashing")
                    val fileHash = Hashing.sha256(path)
                    val size = Files.size(path)

                    val manifest = api.createTransfer(
                        token,
                        CreateTransferRequest(
                            fileName = path.fileName.toString(),
                            size = size,
                            sha256 = fileHash
                        )
                    )

                    updateItemStatus(item.localId, "Uploading")
                    val buffer = ByteArray(manifest.chunkSize)
                    var chunkIndex = 0
                    var uploaded = 0L

                    Files.newInputStream(path).use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) {
                                break
                            }

                            val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                            val chunkHash = Hashing.sha256(chunk)
                            api.uploadChunk(token, manifest.transferId, chunkIndex, chunkHash, chunk)

                            uploaded += read
                            val progress = if (size == 0L) 1.0 else uploaded.toDouble() / size.toDouble()
                            updateItemProgress(item.localId, progress)
                            chunkIndex++
                        }
                    }

                    val completed = api.completeTransfer(token, manifest.transferId)
                    if (completed.success) {
                        updateItemStatus(item.localId, "Completato")
                        updateItemProgress(item.localId, 1.0)
                    } else {
                        updateItemStatus(item.localId, "Errore complete")
                    }
                } catch (exception: Exception) {
                    updateItemStatus(item.localId, "Errore: ${exception.message}")
                }
            }
        } finally {
            uploadInProgress = false
            _uiState.update { it.copy(uploadInProgress = false) }
        }
    }

    fun clearCompletedQueue() {
        _uiState.update { state ->
            state.copy(
                transferQueue = state.transferQueue.filterNot { it.status == "Completato" }
            )
        }
    }

    fun retryFailedQueue() {
        _uiState.update { state ->
            state.copy(
                transferQueue = state.transferQueue.map { item ->
                    if (item.status.startsWith("Errore")) {
                        item.copy(status = "In coda", progress = 0.0)
                    } else {
                        item
                    }
                }
            )
        }
        appendLog("Elementi in errore rimessi in coda")
    }

    fun removeQueueItem(localId: String) {
        _uiState.update { state ->
            state.copy(transferQueue = state.transferQueue.filterNot { it.localId == localId })
        }
    }

    fun clearWarning() {
        _uiState.update { it.copy(blockingWarning = null) }
    }

    fun shutdown() {
        discoveryJob?.cancel()
        devicePollingJob?.cancel()
        client?.close()
        server?.stop()
        scope.cancel()
    }

    private suspend fun discoverHostsInternal(showLog: Boolean) {
        runCatching {
            MdnsDiscovery().discover(1_200)
        }.onSuccess { hosts ->
            _uiState.update { it.copy(discoveredHosts = hosts) }
            if (showLog) {
                appendLog("Trovati ${hosts.size} host in LAN")
            }
        }.onFailure { exception ->
            if (showLog) {
                appendLog("Errore discovery: ${exception.message}")
            }
        }
    }

    private fun startDevicePolling() {
        devicePollingJob?.cancel()
        devicePollingJob = scope.launch {
            while (isActive) {
                refreshConnectedDevicesInternal(showLogOnError = false)
                delay(5_000)
            }
        }
    }

    private suspend fun refreshConnectedDevicesInternal(showLogOnError: Boolean) {
        val api = client
        val token = sessionToken
        if (api == null || token.isNullOrBlank()) {
            return
        }

        runCatching {
            api.devices(token)
        }.onSuccess { devices ->
            _uiState.update { it.copy(connectedDevices = devices) }
        }.onFailure { error ->
            if (showLogOnError) {
                appendLog("Errore aggiornamento dispositivi: ${error.message}")
            }
        }
    }

    private fun appendLog(message: String) {
        _uiState.update { state ->
            val log = "${System.currentTimeMillis()} - $message"
            state.copy(logs = (state.logs + log).takeLast(200))
        }
    }

    private fun expandDroppedPaths(paths: List<Path>): List<Path> {
        val output = mutableListOf<Path>()
        for (path in paths) {
            when {
                Files.isRegularFile(path) -> output.add(path)
                Files.isDirectory(path) -> {
                    Files.walk(path).use { stream ->
                        stream
                            .filter { Files.isRegularFile(it) }
                            .forEach { output.add(it) }
                    }
                }
            }
        }
        return output
    }

    private fun updateItemStatus(localId: String, status: String) {
        _uiState.update { state ->
            state.copy(
                transferQueue = state.transferQueue.map {
                    if (it.localId == localId) it.copy(status = status) else it
                }
            )
        }
    }

    private fun updateItemProgress(localId: String, progress: Double) {
        _uiState.update { state ->
            state.copy(
                transferQueue = state.transferQueue.map {
                    if (it.localId == localId) it.copy(progress = progress) else it
                }
            )
        }
    }
}

data class AppUiState(
    val hostRunning: Boolean = false,
    val hostFingerprint: String = "",
    val localHostId: String = "",
    val hostEndpoint: String = "",
    val autoDiscoveryEnabled: Boolean = false,
    val discoveredHosts: List<HostAnnouncement> = emptyList(),
    val connected: Boolean = false,
    val sessionToken: String = "",
    val connectedHostId: String = "",
    val connectedBaseUrl: String = "",
    val connectedDevices: List<DeviceInfo> = emptyList(),
    val uploadInProgress: Boolean = false,
    val blockingWarning: String? = null,
    val transferQueue: List<TransferQueueItem> = emptyList(),
    val lastSyncPairId: String = "",
    val lastSyncDeltaCount: Int = 0,
    val lastSyncConflictCount: Int = 0,
    val syncConflictItems: List<String> = emptyList(),
    val lastRegisteredMediaId: String = "",
    val lastRegisteredMediaPath: String = "",
    val activeMediaSessionId: String = "",
    val activeMediaMode: String = "",
    val liveSessionId: String = "",
    val liveStreamUrl: String = "",
    val liveStatus: String = "IDLE",
    val logs: List<String> = emptyList()
)

data class TransferQueueItem(
    val localId: String,
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val progress: Double,
    val status: String
)
