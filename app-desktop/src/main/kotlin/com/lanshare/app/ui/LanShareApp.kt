package com.lanshare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanshare.app.viewmodel.AppUiState
import com.lanshare.app.viewmodel.MainViewModel
import com.lanshare.app.viewmodel.TransferQueueItem
import com.lanshare.core.api.model.HostAnnouncement
import com.lanshare.core.api.model.PlaybackMode
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import javax.swing.JFileChooser

private enum class LanShareTab(val label: String) {
    Dashboard("Panoramica"),
    Transfers("Trasferimenti"),
    Sync("Sync"),
    Media("Media"),
    Logs("Log")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanShareApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    var baseUrl by remember { mutableStateOf("https://localhost:8443") }
    var hostId by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("Desktop Client") }
    var hostFilter by remember { mutableStateOf("") }

    var syncHostPath by remember { mutableStateOf("") }
    var syncClientPath by remember { mutableStateOf("") }
    var syncClientDeviceId by remember { mutableStateOf("") }
    var syncPairIdForScan by remember { mutableStateOf("") }

    var mediaPath by remember { mutableStateOf("") }
    var includeSystemAudio by remember { mutableStateOf(true) }

    var selectedTab by remember { mutableStateOf(LanShareTab.Dashboard) }

    LaunchedEffect(state.discoveredHosts) {
        if (state.discoveredHosts.size == 1) {
            val host = state.discoveredHosts.first()
            if (hostId.isBlank()) {
                hostId = host.hostId
            }
            if (baseUrl == "https://localhost:8443" || baseUrl.isBlank()) {
                val hostAddress = host.address ?: "localhost"
                baseUrl = "https://$hostAddress:${host.apiPort}"
            }
        }
    }

    val filteredHosts = remember(state.discoveredHosts, hostFilter) {
        val query = hostFilter.trim().lowercase()
        if (query.isBlank()) {
            state.discoveredHosts
        } else {
            state.discoveredHosts.filter { host ->
                host.name.lowercase().contains(query) ||
                    host.hostId.lowercase().contains(query) ||
                    (host.address ?: "").lowercase().contains(query)
            }
        }
    }

    val firstFilteredHost = filteredHosts.firstOrNull()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LanShare Desktop") },
                actions = {
                    Text(
                        if (state.connected) "Connesso" else "Offline",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(state)

            if (state.blockingWarning != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            state.blockingWarning ?: "",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearWarning() }) {
                            Text("Chiudi")
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                LanShareTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    LanShareTab.Dashboard -> {
                        HostCard(
                            state = state,
                            filteredHosts = filteredHosts,
                            hostFilter = hostFilter,
                            onHostFilterChange = { hostFilter = it },
                            onStartHost = viewModel::startHost,
                            onStopHost = viewModel::stopHost,
                            onDiscoverHosts = viewModel::discoverHosts,
                            onToggleAutoDiscovery = viewModel::setAutoDiscovery,
                            onUseLocalHost = {
                                hostId = state.localHostId
                                baseUrl = state.hostEndpoint
                            },
                            onUseHost = { host ->
                                hostId = host.hostId
                                baseUrl = hostBaseUrl(host)
                            },
                            onQuickConnect = { host ->
                                hostId = host.hostId
                                baseUrl = hostBaseUrl(host)
                                viewModel.quickConnect(host, deviceName)
                            }
                        )

                        ConnectionCard(
                            state = state,
                            baseUrl = baseUrl,
                            hostId = hostId,
                            deviceName = deviceName,
                            firstFilteredHost = firstFilteredHost,
                            onBaseUrlChange = { baseUrl = it },
                            onHostIdChange = { hostId = it },
                            onDeviceNameChange = { deviceName = it },
                            onConnect = { viewModel.connectToHost(baseUrl, hostId, deviceName) },
                            onDisconnect = viewModel::disconnectClient,
                            onUploadQueue = viewModel::uploadQueuedFiles,
                            onRefreshDevices = viewModel::refreshConnectedDevices,
                            onUseSuggestedHost = { host ->
                                hostId = host.hostId
                                baseUrl = hostBaseUrl(host)
                            }
                        )

                        if (state.connected) {
                            ConnectedDevicesCard(state)
                        }
                    }

                    LanShareTab.Transfers -> {
                        DragDropCard(onFilesPicked = viewModel::onFilesDropped)
                        TransferQueueCard(
                            state = state,
                            onRetryFailed = viewModel::retryFailedQueue,
                            onClearCompleted = viewModel::clearCompletedQueue,
                            onClearQueue = viewModel::clearQueue,
                            onRemoveQueueItem = viewModel::removeQueueItem
                        )
                    }

                    LanShareTab.Sync -> {
                        SyncCard(
                            state = state,
                            syncHostPath = syncHostPath,
                            syncClientPath = syncClientPath,
                            syncClientDeviceId = syncClientDeviceId,
                            syncPairIdForScan = syncPairIdForScan,
                            onSyncHostPathChange = { syncHostPath = it },
                            onSyncClientPathChange = { syncClientPath = it },
                            onSyncClientDeviceIdChange = { syncClientDeviceId = it },
                            onSyncPairIdForScanChange = { syncPairIdForScan = it },
                            onUseLastPairId = { syncPairIdForScan = state.lastSyncPairId },
                            onCreatePair = {
                                viewModel.createSyncPair(syncHostPath, syncClientPath, syncClientDeviceId)
                            },
                            onRunScan = { viewModel.runSyncScan(syncPairIdForScan) }
                        )
                    }

                    LanShareTab.Media -> {
                        MediaCard(
                            state = state,
                            mediaPath = mediaPath,
                            includeSystemAudio = includeSystemAudio,
                            onMediaPathChange = { mediaPath = it },
                            onIncludeSystemAudioChange = { includeSystemAudio = it },
                            onBrowseMedia = {
                                val selected = pickSingleFileFromDialog()
                                if (selected != null) {
                                    mediaPath = selected.toString()
                                }
                            },
                            onRegisterMedia = { viewModel.registerMedia(mediaPath) },
                            onStartHostSyncSession = {
                                viewModel.startMediaSession(PlaybackMode.HOST_SYNC)
                            },
                            onStartIndependentSession = {
                                viewModel.startMediaSession(PlaybackMode.INDEPENDENT)
                            },
                            onStartLive = { viewModel.startLive(includeSystemAudio) },
                            onStopLive = viewModel::stopLive
                        )
                    }

                    LanShareTab.Logs -> {
                        LogsCard(
                            state = state,
                            onClearLogs = viewModel::clearLogs
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: AppUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Metric("Host", if (state.hostRunning) "Attivo" else "Fermo")
            Metric("LAN", "${state.discoveredHosts.size} host")
            Metric("Queue", "${state.transferQueue.size} file")
            Metric("Live", state.liveStatus)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HostCard(
    state: AppUiState,
    filteredHosts: List<HostAnnouncement>,
    hostFilter: String,
    onHostFilterChange: (String) -> Unit,
    onStartHost: () -> Unit,
    onStopHost: () -> Unit,
    onDiscoverHosts: () -> Unit,
    onToggleAutoDiscovery: (Boolean) -> Unit,
    onUseLocalHost: () -> Unit,
    onUseHost: (HostAnnouncement) -> Unit,
    onQuickConnect: (HostAnnouncement) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Host locale", fontWeight = FontWeight.SemiBold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.hostRunning) {
                    Button(onClick = onStartHost) {
                        Text("Avvia Host")
                    }
                } else {
                    OutlinedButton(onClick = onStopHost) {
                        Text("Ferma Host")
                    }
                }
                OutlinedButton(onClick = onDiscoverHosts) {
                    Text("Discovery LAN")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Auto-discovery")
                Switch(
                    checked = state.autoDiscoveryEnabled,
                    onCheckedChange = onToggleAutoDiscovery
                )
            }

            Text("Fingerprint: ${if (state.hostFingerprint.isBlank()) "-" else state.hostFingerprint}")

            if (state.hostRunning) {
                Text("Host ID locale: ${state.localHostId.take(8)}...")
                Text("Endpoint locale: ${state.hostEndpoint}")
                OutlinedButton(
                    onClick = onUseLocalHost,
                    enabled = state.localHostId.isNotBlank() && state.hostEndpoint.isNotBlank()
                ) {
                    Text("Usa Host Locale")
                }
            }

            OutlinedTextField(
                value = hostFilter,
                onValueChange = onHostFilterChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filtro host") },
                singleLine = true
            )

            Text("Host remoti trovati: ${filteredHosts.size}")
            if (filteredHosts.isEmpty()) {
                Text("Nessun host remoto trovato con questo filtro")
            }

            filteredHosts.forEach { host ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(host.name, fontWeight = FontWeight.SemiBold)
                            Text("id=${host.hostId.take(8)}...")
                            val address = host.address ?: "localhost"
                            Text("$address:${host.apiPort}")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { onUseHost(host) }) {
                                Text("Usa")
                            }
                            Button(onClick = { onQuickConnect(host) }) {
                                Text("Connetti")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: AppUiState,
    baseUrl: String,
    hostId: String,
    deviceName: String,
    firstFilteredHost: HostAnnouncement?,
    onBaseUrlChange: (String) -> Unit,
    onHostIdChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onUploadQueue: () -> Unit,
    onRefreshDevices: () -> Unit,
    onUseSuggestedHost: (HostAnnouncement) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Connessione Client", fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL host") },
                singleLine = true
            )
            OutlinedTextField(
                value = hostId,
                onValueChange = onHostIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Host ID (opzionale)") },
                singleLine = true
            )
            OutlinedTextField(
                value = deviceName,
                onValueChange = onDeviceNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome dispositivo") },
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.connected) {
                    OutlinedButton(onClick = onDisconnect) {
                        Text("Disconnetti")
                    }
                } else {
                    Button(onClick = onConnect, enabled = baseUrl.isNotBlank()) {
                        Text("Connetti")
                    }
                }

                OutlinedButton(
                    onClick = onUploadQueue,
                    enabled = state.connected && !state.uploadInProgress
                ) {
                    Text(if (state.uploadInProgress) "Upload in corso" else "Invia Coda")
                }

                OutlinedButton(onClick = onRefreshDevices, enabled = state.connected) {
                    Text("Aggiorna dispositivi")
                }
            }

            OutlinedButton(
                onClick = {
                    if (firstFilteredHost != null) {
                        onUseSuggestedHost(firstFilteredHost)
                    }
                },
                enabled = firstFilteredHost != null
            ) {
                Text("Prefill da host trovato")
            }

            Text("Connesso: ${if (state.connected) "SI" else "NO"}")
            if (state.connected) {
                Text("Host: ${state.connectedHostId.take(8)}...")
                Text("URL: ${state.connectedBaseUrl}")
                Text("Sessione: ${state.sessionToken.take(12)}...")
            }
        }
    }
}

@Composable
private fun DragDropCard(onFilesPicked: (List<Path>) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Drag & Drop", fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = {
                    val picked = pickFilesOrDirectoriesFromDialog()
                    if (picked.isNotEmpty()) {
                        onFilesPicked(picked)
                    }
                }
            ) {
                Text("Aggiungi file/cartelle")
            }
            SwingPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                factory = {
                    FileDropPanel { files ->
                        onFilesPicked(files)
                    }
                }
            )
        }
    }
}

@Composable
private fun TransferQueueCard(
    state: AppUiState,
    onRetryFailed: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearQueue: () -> Unit,
    onRemoveQueueItem: (String) -> Unit
) {
    val failedCount = state.transferQueue.count { it.status.startsWith("Errore") }
    val completedCount = state.transferQueue.count { it.status == "Completato" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Coda Trasferimenti", fontWeight = FontWeight.SemiBold)
            Text("Totale: ${state.transferQueue.size} - Completati: $completedCount - Errori: $failedCount")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetryFailed) {
                    Text("Riprova errori")
                }
                OutlinedButton(onClick = onClearCompleted) {
                    Text("Pulisci completati")
                }
                OutlinedButton(onClick = onClearQueue, enabled = state.transferQueue.isNotEmpty()) {
                    Text("Svuota coda")
                }
            }

            if (state.transferQueue.isEmpty()) {
                Text("Nessun file in coda")
            }

            state.transferQueue.forEach { item ->
                TransferQueueItemRow(item = item, onRemove = { onRemoveQueueItem(item.localId) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TransferQueueItemRow(item: TransferQueueItem, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${humanSize(item.sizeBytes)} - ${item.status}")
            }
            TextButton(onClick = onRemove) {
                Text("Rimuovi")
            }
        }

        LinearProgressIndicator(
            progress = { item.progress.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SyncCard(
    state: AppUiState,
    syncHostPath: String,
    syncClientPath: String,
    syncClientDeviceId: String,
    syncPairIdForScan: String,
    onSyncHostPathChange: (String) -> Unit,
    onSyncClientPathChange: (String) -> Unit,
    onSyncClientDeviceIdChange: (String) -> Unit,
    onSyncPairIdForScanChange: (String) -> Unit,
    onUseLastPairId: () -> Unit,
    onCreatePair: () -> Unit,
    onRunScan: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Sync Cartelle", fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = syncHostPath,
                onValueChange = onSyncHostPathChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Percorso host") },
                singleLine = true
            )
            OutlinedTextField(
                value = syncClientPath,
                onValueChange = onSyncClientPathChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Percorso client") },
                singleLine = true
            )
            OutlinedTextField(
                value = syncClientDeviceId,
                onValueChange = onSyncClientDeviceIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Client device ID") },
                singleLine = true
            )
            OutlinedTextField(
                value = syncPairIdForScan,
                onValueChange = onSyncPairIdForScanChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pair ID per scan (opzionale)") },
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCreatePair) {
                    Text("Crea Pair")
                }
                OutlinedButton(onClick = onRunScan) {
                    Text("Esegui Scan")
                }
                OutlinedButton(
                    onClick = onUseLastPairId,
                    enabled = state.lastSyncPairId.isNotBlank()
                ) {
                    Text("Usa ultimo Pair ID")
                }
            }

            Text("Ultimo Pair ID: ${state.lastSyncPairId.ifBlank { "-" }}")
            Text("Delta: ${state.lastSyncDeltaCount} - Conflitti: ${state.lastSyncConflictCount}")
            state.syncConflictItems.take(5).forEach { item ->
                Text(item)
            }
        }
    }
}

@Composable
private fun MediaCard(
    state: AppUiState,
    mediaPath: String,
    includeSystemAudio: Boolean,
    onMediaPathChange: (String) -> Unit,
    onIncludeSystemAudioChange: (Boolean) -> Unit,
    onBrowseMedia: () -> Unit,
    onRegisterMedia: () -> Unit,
    onStartHostSyncSession: () -> Unit,
    onStartIndependentSession: () -> Unit,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Media & Live", fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = mediaPath,
                onValueChange = onMediaPathChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Percorso file video") },
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBrowseMedia) {
                    Text("Sfoglia")
                }
                OutlinedButton(onClick = onRegisterMedia) {
                    Text("Registra Media")
                }
            }

            Text("Media ID: ${state.lastRegisteredMediaId.ifBlank { "-" }}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onStartHostSyncSession) {
                    Text("Sessione HOST_SYNC")
                }
                OutlinedButton(onClick = onStartIndependentSession) {
                    Text("Sessione INDEPENDENT")
                }
            }

            Text("Sessione media: ${state.activeMediaSessionId.ifBlank { "-" }} (${state.activeMediaMode.ifBlank { "-" }})")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Audio sistema live")
                Switch(
                    checked = includeSystemAudio,
                    onCheckedChange = onIncludeSystemAudioChange
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onStartLive) {
                    Text("Avvia Live")
                }
                OutlinedButton(onClick = onStopLive) {
                    Text("Ferma Live")
                }
            }

            Text("Live stato: ${state.liveStatus}")
            if (state.liveStreamUrl.isNotBlank()) {
                Text("Stream URL: ${state.liveStreamUrl}")
            }
        }
    }
}

@Composable
private fun ConnectedDevicesCard(state: AppUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Dispositivi Connessi", fontWeight = FontWeight.SemiBold)
            if (state.connectedDevices.isEmpty()) {
                Text("Nessun dispositivo disponibile")
            }
            state.connectedDevices.forEach { device ->
                Text("${device.deviceName} (${device.role}) - ${device.ipAddress} - ${device.status}")
            }
        }
    }
}

@Composable
private fun LogsCard(state: AppUiState, onClearLogs: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Log", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = onClearLogs, enabled = state.logs.isNotEmpty()) {
                    Text("Pulisci log")
                }
            }

            if (state.logs.isEmpty()) {
                Text("Nessun log")
            }
            state.logs.takeLast(40).forEach { line ->
                Text(formatLogLine(line))
            }
        }
    }
}

private fun hostBaseUrl(host: HostAnnouncement): String {
    val hostAddress = host.address ?: "localhost"
    return "https://$hostAddress:${host.apiPort}"
}

private fun formatLogLine(raw: String): String {
    val separator = raw.indexOf(" - ")
    if (separator <= 0) {
        return raw
    }

    val millis = raw.substring(0, separator).toLongOrNull() ?: return raw
    val message = raw.substring(separator + 3)
    val localTime = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .withNano(0)

    return "$localTime - $message"
}

private fun humanSize(size: Long): String {
    if (size < 1024) {
        return "$size B"
    }
    val kb = size / 1024.0
    if (kb < 1024) {
        return "%.1f KB".format(kb)
    }
    val mb = kb / 1024.0
    if (mb < 1024) {
        return "%.1f MB".format(mb)
    }
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

private fun pickFilesOrDirectoriesFromDialog(): List<Path> {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        isMultiSelectionEnabled = true
        dialogTitle = "Seleziona file o cartelle"
    }

    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) {
        return emptyList()
    }

    val selected = chooser.selectedFiles?.toList().orEmpty().ifEmpty {
        chooser.selectedFile?.let { listOf(it) } ?: emptyList()
    }

    return selected.map { it.toPath() }
}

private fun pickSingleFileFromDialog(): Path? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = false
        dialogTitle = "Seleziona file video"
    }

    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) {
        return null
    }
    return chooser.selectedFile?.toPath()
}
