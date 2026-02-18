package com.lanshare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanshare.app.viewmodel.MainViewModel
import java.nio.file.Path
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanShareApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    var baseUrl by remember { mutableStateOf("https://localhost:8443") }
    var hostId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("Desktop Client") }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("LanShare Desktop", fontWeight = FontWeight.Bold)
        Text("Condivisione LAN sicura con drag-and-drop", color = Color(0xFF3F51B5))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Host locale", fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.hostRunning) {
                        Button(onClick = { viewModel.startHost() }) {
                            Text("Avvia Host")
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.stopHost() }) {
                            Text("Ferma Host")
                        }
                    }

                    OutlinedButton(onClick = { viewModel.discoverHosts() }) {
                        Text("Discovery LAN")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Auto-discovery")
                    Switch(
                        checked = state.autoDiscoveryEnabled,
                        onCheckedChange = { viewModel.setAutoDiscovery(it) }
                    )
                }

                Text("PIN: ${if (state.hostPin.isBlank()) "-" else state.hostPin}")
                Text("Fingerprint: ${if (state.hostFingerprint.isBlank()) "-" else state.hostFingerprint}")
                Text("Host trovati: ${state.discoveredHosts.size}")

                state.discoveredHosts.forEach { host ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${host.name} - id=${host.hostId.take(8)}...")
                            val address = host.address ?: "localhost"
                            Text("$address:${host.apiPort}")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    hostId = host.hostId
                                    val hostAddress = host.address ?: "localhost"
                                    baseUrl = "https://$hostAddress:${host.apiPort}"
                                }
                            ) {
                                Text("Usa")
                            }

                            Button(
                                onClick = {
                                    hostId = host.hostId
                                    val hostAddress = host.address ?: "localhost"
                                    baseUrl = "https://$hostAddress:${host.apiPort}"
                                    viewModel.quickConnect(host, pin, deviceName)
                                }
                            ) {
                                Text("Connetti")
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connessione Client", fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL host") }
                )
                OutlinedTextField(
                    value = hostId,
                    onValueChange = { hostId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Host ID") }
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    modifier = Modifier.width(200.dp),
                    label = { Text("PIN") }
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome dispositivo") }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.connected) {
                        OutlinedButton(onClick = { viewModel.disconnectClient() }) {
                            Text("Disconnetti")
                        }
                    } else {
                        Button(onClick = { viewModel.connectToHost(baseUrl, hostId, pin, deviceName) }) {
                            Text("Connetti")
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.uploadQueuedFiles() },
                        enabled = state.connected && !state.uploadInProgress
                    ) {
                        Text(if (state.uploadInProgress) "Upload in corso" else "Invia Coda")
                    }

                    OutlinedButton(
                        onClick = { viewModel.refreshConnectedDevices() },
                        enabled = state.connected
                    ) {
                        Text("Aggiorna dispositivi")
                    }
                }

                Text("Connesso: ${if (state.connected) "SI" else "NO"}")
                if (state.connected) {
                    Text("Host: ${state.connectedHostId.take(8)}...")
                    Text("URL: ${state.connectedBaseUrl}")
                    Text("Sessione: ${state.sessionToken.take(12)}...")
                }
            }
        }

        if (state.blockingWarning != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(state.blockingWarning ?: "")
                    TextButton(onClick = { viewModel.clearWarning() }) {
                        Text("Chiudi")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Drag & Drop", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val picked = pickFilesOrDirectoriesFromDialog()
                            if (picked.isNotEmpty()) {
                                viewModel.onFilesDropped(picked)
                            }
                        }
                    ) {
                        Text("Aggiungi file/cartelle")
                    }
                }
                SwingPanel(
                    modifier = Modifier.fillMaxWidth().height(130.dp),
                    factory = {
                        FileDropPanel { files ->
                            viewModel.onFilesDropped(files)
                        }
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coda Trasferimenti", fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.retryFailedQueue() }) {
                        Text("Riprova errori")
                    }
                    OutlinedButton(onClick = { viewModel.clearCompletedQueue() }) {
                        Text("Pulisci completati")
                    }
                }

                if (state.transferQueue.isEmpty()) {
                    Text("Nessun file in coda")
                }

                state.transferQueue.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(item.fileName)
                                Text("${humanSize(item.sizeBytes)} - ${item.status}")
                            }
                            TextButton(onClick = { viewModel.removeQueueItem(item.localId) }) {
                                Text("Rimuovi")
                            }
                        }

                        LinearProgressIndicator(
                            progress = { item.progress.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        if (state.connected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Log", fontWeight = FontWeight.SemiBold)
                if (state.logs.isEmpty()) {
                    Text("Nessun log")
                }
                state.logs.takeLast(12).forEach { line ->
                    Text(line)
                }
            }
        }
    }
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
