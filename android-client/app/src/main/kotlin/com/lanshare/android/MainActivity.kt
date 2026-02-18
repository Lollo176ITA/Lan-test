package com.lanshare.android

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LanShareAndroidApp()
            }
        }
    }
}

@Composable
private fun LanShareAndroidApp(vm: LanShareAndroidViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    var deviceName by rememberSaveable { mutableStateOf(android.os.Build.MODEL ?: "Android Client") }
    var hostId by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.discoveredHosts) {
        if (state.discoveredHosts.size == 1 && hostId.isBlank()) {
            val host = state.discoveredHosts.first()
            hostId = host.hostId
            baseUrl = "https://${host.address}:${host.port}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("LanShare Android", fontWeight = FontWeight.Bold)
        Text("Client LAN (discovery host + join TLS)", color = Color(0xFF1565C0))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Discovery LAN", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.refreshDiscovery() }) {
                        Text("Aggiorna")
                    }
                    if (state.discoveryRunning) {
                        OutlinedButton(onClick = { vm.stopDiscovery() }) {
                            Text("Ferma")
                        }
                    } else {
                        Button(onClick = { vm.startDiscovery() }) {
                            Text("Avvia")
                        }
                    }
                }
                Text("Host trovati: ${state.discoveredHosts.size}")
                Text("La discovery mostra gli host disponibili in LAN.")
                if (state.discoveredHosts.isEmpty()) {
                    Text("Nessun host trovato. Avvia l'host desktop su un altro device.")
                }

                state.discoveredHosts.forEach { host ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${host.name} - id=${host.hostId.take(8)}...")
                            Text("${host.address}:${host.port}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    hostId = host.hostId
                                    baseUrl = "https://${host.address}:${host.port}"
                                }
                            ) {
                                Text("Usa")
                            }
                            Button(
                                onClick = {
                                    val selectedHostId = host.hostId
                                    val selectedBaseUrl = "https://${host.address}:${host.port}"
                                    hostId = selectedHostId
                                    baseUrl = selectedBaseUrl
                                    vm.connect(selectedBaseUrl, selectedHostId, deviceName)
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
                Text("Connessione", fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL host (https://ip:porta)") }
                )
                OutlinedTextField(
                    value = hostId,
                    onValueChange = { hostId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Host ID (opzionale)") }
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome dispositivo") }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.connected) {
                        OutlinedButton(onClick = { vm.disconnect() }) {
                            Text("Disconnetti")
                        }
                    } else {
                        Button(onClick = { vm.connect(baseUrl, hostId, deviceName) }) {
                            Text("Connetti")
                        }
                    }
                    OutlinedButton(
                        onClick = { vm.refreshDevices() },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(state.blockingWarning ?: "")
                    TextButton(onClick = { vm.clearWarning() }) {
                        Text("Chiudi")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dispositivi Connessi", fontWeight = FontWeight.SemiBold)
                if (state.connectedDevices.isEmpty()) {
                    Text("Nessun dispositivo disponibile")
                }
                state.connectedDevices.forEach { device ->
                    Text("${device.deviceName} (${device.role}) - ${device.ipAddress} - ${device.status}")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Log", fontWeight = FontWeight.SemiBold)
                state.logs.takeLast(12).forEach { line ->
                    Text(line)
                }
            }
        }
    }
}

class LanShareAndroidViewModel(application: Application) : AndroidViewModel(application) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    private val httpClient = createUnsafeOkHttpClient()
    private val discovery = AndroidLanDiscovery(
        context = application.applicationContext,
        onHostFound = { discovered -> onHostDiscovered(discovered) },
        onError = { message -> appendLog("Discovery: $message") }
    )

    private val _uiState = MutableStateFlow(AndroidUiState())
    val uiState: StateFlow<AndroidUiState> = _uiState.asStateFlow()

    init {
        refreshDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        discovery.close()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    fun startDiscovery() {
        discovery.start()
        _uiState.update { it.copy(discoveryRunning = true) }
        appendLog("Discovery avviata")
    }

    fun stopDiscovery() {
        discovery.stop()
        _uiState.update { it.copy(discoveryRunning = false) }
        appendLog("Discovery fermata")
    }

    fun refreshDiscovery() {
        _uiState.update { it.copy(discoveredHosts = emptyList()) }
        discovery.start()
        _uiState.update { it.copy(discoveryRunning = true) }
        appendLog("Discovery aggiornata")
        viewModelScope.launch {
            delay(2_000)
            discovery.stop()
            _uiState.update { it.copy(discoveryRunning = false) }
        }
    }

    fun connect(baseUrl: String, hostId: String, deviceName: String) {
        val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
        if (normalizedBaseUrl.isBlank()) {
            _uiState.update {
                it.copy(blockingWarning = "Base URL host obbligatorio")
            }
            return
        }
        val effectiveHostId = hostId.ifBlank { "auto" }

        viewModelScope.launch {
            runCatching {
                val payload = json.encodeToString(
                    JoinRequest(
                        hostId = effectiveHostId,
                        deviceName = deviceName.ifBlank { "Android Client" },
                        devicePublicKey = "android-client"
                    )
                )
                val request = Request.Builder()
                    .url("$normalizedBaseUrl/api/v1/join")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                val body = executeRequest(request)
                json.decodeFromString<JoinResponse>(body)
            }.onSuccess { join ->
                _uiState.update {
                    it.copy(
                        connected = true,
                        connectedHostId = effectiveHostId,
                        connectedBaseUrl = normalizedBaseUrl,
                        sessionToken = join.sessionToken,
                        hostFingerprint = join.hostFingerprint,
                        blockingWarning = null
                    )
                }
                appendLog("Connesso a $effectiveHostId")
                refreshDevices()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        connected = false,
                        connectedHostId = "",
                        connectedBaseUrl = "",
                        sessionToken = "",
                        connectedDevices = emptyList(),
                        blockingWarning = "Connessione fallita: ${error.message}"
                    )
                }
                appendLog("Errore connessione: ${error.message}")
            }
        }
    }

    fun disconnect() {
        _uiState.update {
            it.copy(
                connected = false,
                connectedHostId = "",
                connectedBaseUrl = "",
                sessionToken = "",
                connectedDevices = emptyList()
            )
        }
        appendLog("Client disconnesso")
    }

    fun refreshDevices() {
        val token = _uiState.value.sessionToken
        val baseUrl = _uiState.value.connectedBaseUrl
        if (token.isBlank() || baseUrl.isBlank()) {
            return
        }

        viewModelScope.launch {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/devices")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                val body = executeRequest(request)
                json.decodeFromString<List<DeviceInfo>>(body)
            }.onSuccess { devices ->
                _uiState.update { it.copy(connectedDevices = devices) }
                appendLog("Dispositivi aggiornati: ${devices.size}")
            }.onFailure { error ->
                appendLog("Errore dispositivi: ${error.message}")
            }
        }
    }

    fun clearWarning() {
        _uiState.update { it.copy(blockingWarning = null) }
    }

    private fun onHostDiscovered(host: DiscoveredHost) {
        _uiState.update { state ->
            val remaining = state.discoveredHosts.filterNot {
                it.hostId == host.hostId || (it.address == host.address && it.port == host.port)
            }
            state.copy(discoveredHosts = (remaining + host).sortedBy { it.name.lowercase() })
        }
    }

    private suspend fun executeRequest(request: Request): String = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${body.take(200)}")
            }
            body
        }
    }

    private fun appendLog(message: String) {
        _uiState.update { state ->
            state.copy(
                logs = (state.logs + "${System.currentTimeMillis()} - $message").takeLast(200)
            )
        }
    }
}

private class AndroidLanDiscovery(
    context: Context,
    private val onHostFound: (DiscoveredHost) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val resolving = mutableSetOf<String>()

    fun start() {
        stop()
        acquireMulticastLock()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.startsWith(SERVICE_TYPE_PREFIX)) {
                    return
                }
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError("Start discovery failed: $errorCode")
                stop()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError("Stop discovery failed: $errorCode")
                stop()
            }
        }

        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { error ->
            onError("discoverServices error: ${error.message}")
            discoveryListener = null
        }
    }

    fun stop() {
        val listener = discoveryListener
        discoveryListener = null
        if (listener != null) {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        resolving.clear()
        releaseMulticastLock()
    }

    override fun close() {
        stop()
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        val key = "${serviceInfo.serviceName}:${serviceInfo.serviceType}"
        if (!resolving.add(key)) {
            return
        }

        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving.remove(key)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    resolving.remove(key)

                    val address = serviceInfo.host?.hostAddress ?: return
                    if (serviceInfo.port <= 0) {
                        return
                    }

                    val txt = serviceInfo.attributes.mapValues { (_, value) ->
                        String(value, Charsets.UTF_8)
                    }

                    val hostId = txt["hostId"]?.takeIf { it.isNotBlank() }
                        ?: "${serviceInfo.serviceName}-${address}:${serviceInfo.port}"
                    val name = txt["name"]?.takeIf { it.isNotBlank() } ?: serviceInfo.serviceName
                    val version = txt["version"]?.takeIf { it.isNotBlank() } ?: "unknown"
                    val fingerprintShort = txt["fingerprintShort"] ?: ""

                    onHostFound(
                        DiscoveredHost(
                            hostId = hostId,
                            name = name,
                            address = address,
                            port = serviceInfo.port,
                            version = version,
                            fingerprintShort = fingerprintShort
                        )
                    )
                }
            }
        )
    }

    companion object {
        private const val SERVICE_TYPE = "_lanshare._tcp."
        private const val SERVICE_TYPE_PREFIX = "_lanshare._tcp"
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            multicastLock = wifiManager?.createMulticastLock("lanshare-mdns-lock")?.apply {
                setReferenceCounted(false)
            }
        }
        runCatching { multicastLock?.acquire() }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
    }
}

private fun createUnsafeOkHttpClient(): OkHttpClient {
    val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
    val socketFactory: SSLSocketFactory = sslContext.socketFactory
    val hostnameVerifier = HostnameVerifier { _, _ -> true }

    return OkHttpClient.Builder()
        .sslSocketFactory(socketFactory, trustAllManager)
        .hostnameVerifier(hostnameVerifier)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()
}

data class AndroidUiState(
    val discoveryRunning: Boolean = false,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val connected: Boolean = false,
    val connectedHostId: String = "",
    val connectedBaseUrl: String = "",
    val sessionToken: String = "",
    val hostFingerprint: String = "",
    val blockingWarning: String? = null,
    val connectedDevices: List<DeviceInfo> = emptyList(),
    val logs: List<String> = emptyList()
)

data class DiscoveredHost(
    val hostId: String,
    val name: String,
    val address: String,
    val port: Int,
    val version: String,
    val fingerprintShort: String
)

@Serializable
data class JoinRequest(
    val hostId: String,
    val deviceName: String,
    val devicePublicKey: String
)

@Serializable
data class JoinResponse(
    val sessionToken: String,
    val hostFingerprint: String
)

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val role: String,
    val status: String,
    val ipAddress: String
)
