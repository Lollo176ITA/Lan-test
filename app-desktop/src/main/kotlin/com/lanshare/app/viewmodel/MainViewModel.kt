package com.lanshare.app.viewmodel

import com.lanshare.core.api.model.CreateTransferRequest
import com.lanshare.core.api.model.HostAnnouncement
import com.lanshare.core.api.model.JoinRequest
import com.lanshare.core.network.LanShareClient
import com.lanshare.core.network.LanShareConfig
import com.lanshare.core.network.LanShareServer
import com.lanshare.core.network.MdnsDiscovery
import com.lanshare.core.network.TrustedHostStore
import com.lanshare.core.transfer.Hashing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    @Volatile
    private var uploadInProgress: Boolean = false

    fun startHost() {
        scope.launch {
            if (server != null) {
                appendLog("Host gia avviato")
                return@launch
            }

            Files.createDirectories(appDataDir)
            val localServer = LanShareServer(
                LanShareConfig(
                    hostName = "LanShare-${UUID.randomUUID().toString().take(4)}",
                    advertisedHost = "localhost",
                    storageRoot = appDataDir.resolve("host")
                )
            )

            localServer.start(wait = false)
            server = localServer

            _uiState.update {
                it.copy(
                    hostRunning = true,
                    hostPin = localServer.currentPin(),
                    hostFingerprint = localServer.fingerprint().take(16)
                )
            }
            appendLog("Host avviato con PIN ${localServer.currentPin()}")
        }
    }

    fun stopHost() {
        scope.launch {
            server?.stop()
            server = null
            _uiState.update {
                it.copy(
                    hostRunning = false,
                    hostPin = "",
                    hostFingerprint = ""
                )
            }
            appendLog("Host fermato")
        }
    }

    fun discoverHosts() {
        scope.launch {
            try {
                val hosts = MdnsDiscovery().discover(1_200)
                _uiState.update { it.copy(discoveredHosts = hosts) }
                appendLog("Trovati ${hosts.size} host in LAN")
            } catch (exception: Exception) {
                appendLog("Errore discovery: ${exception.message}")
            }
        }
    }

    fun connectToHost(baseUrl: String, hostId: String, pin: String, deviceName: String) {
        scope.launch {
            if (hostId.isBlank() || pin.isBlank()) {
                _uiState.update {
                    it.copy(blockingWarning = "Inserisci hostId e PIN prima di connetterti")
                }
                return@launch
            }

            try {
                client?.close()
                val newClient = LanShareClient(baseUrl)
                val join = newClient.join(
                    JoinRequest(
                        hostId = hostId,
                        pin = pin,
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
                        connectedBaseUrl = baseUrl
                    )
                }
                appendLog("Connesso all'host $hostId")
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
        }
    }

    fun clearWarning() {
        _uiState.update { it.copy(blockingWarning = null) }
    }

    fun shutdown() {
        client?.close()
        server?.stop()
        scope.cancel()
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
    val hostPin: String = "",
    val hostFingerprint: String = "",
    val discoveredHosts: List<HostAnnouncement> = emptyList(),
    val connected: Boolean = false,
    val sessionToken: String = "",
    val connectedHostId: String = "",
    val connectedBaseUrl: String = "",
    val blockingWarning: String? = null,
    val transferQueue: List<TransferQueueItem> = emptyList(),
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
