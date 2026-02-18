package com.lanshare.core.network

import com.lanshare.core.api.model.CompleteTransferResponse
import com.lanshare.core.api.model.CreateTransferRequest
import com.lanshare.core.api.model.DeviceInfo
import com.lanshare.core.api.model.JoinRequest
import com.lanshare.core.api.model.JoinResponse
import com.lanshare.core.api.model.LiveSessionState
import com.lanshare.core.api.model.LiveStartRequest
import com.lanshare.core.api.model.LiveStopRequest
import com.lanshare.core.api.model.MediaRegisterRequest
import com.lanshare.core.api.model.MediaRegisterResponse
import com.lanshare.core.api.model.MediaSession
import com.lanshare.core.api.model.MediaSessionRequest
import com.lanshare.core.api.model.SyncPair
import com.lanshare.core.api.model.SyncPairRequest
import com.lanshare.core.api.model.SyncScanRequest
import com.lanshare.core.api.model.SyncScanResponse
import com.lanshare.core.api.model.TransferManifest
import com.lanshare.core.api.model.TransferResume
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class LanShareClient(
    baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AutoCloseable {
    private val trustAll = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    }

    private val client = HttpClient(CIO) {
        engine {
            https {
                trustManager = trustAll
            }
        }

        install(ContentNegotiation) {
            json(this@LanShareClient.json)
        }

        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun join(request: JoinRequest): JoinResponse {
        return client.post("/api/v1/join") {
            setBody(request)
        }.body()
    }

    suspend fun devices(token: String): List<DeviceInfo> {
        return client.get("/api/v1/devices") {
            bearerAuth(token)
        }.body()
    }

    suspend fun createTransfer(token: String, request: CreateTransferRequest): TransferManifest {
        return client.post("/api/v1/transfers") {
            bearerAuth(token)
            setBody(request)
        }.body()
    }

    suspend fun uploadChunk(token: String, transferId: String, index: Int, chunkHash: String, bytes: ByteArray) {
        client.put("/api/v1/transfers/$transferId/chunks/$index") {
            bearerAuth(token)
            header("X-Chunk-Sha256", chunkHash)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
    }

    suspend fun resumeStatus(token: String, transferId: String): TransferResume {
        return client.get("/api/v1/transfers/$transferId/resume") {
            bearerAuth(token)
        }.body()
    }

    suspend fun completeTransfer(token: String, transferId: String): CompleteTransferResponse {
        return client.post("/api/v1/transfers/$transferId/complete") {
            bearerAuth(token)
        }.body()
    }

    suspend fun createSyncPair(token: String, request: SyncPairRequest): SyncPair {
        return client.post("/api/v1/sync/pairs") {
            bearerAuth(token)
            setBody(request)
        }.body()
    }

    suspend fun syncScan(token: String, request: SyncScanRequest = SyncScanRequest()): SyncScanResponse {
        return client.post("/api/v1/sync/scan") {
            bearerAuth(token)
            setBody(request)
        }.body()
    }

    suspend fun registerMedia(token: String, path: String): MediaRegisterResponse {
        return client.post("/api/v1/media/register") {
            bearerAuth(token)
            setBody(MediaRegisterRequest(path))
        }.body()
    }

    suspend fun createMediaSession(token: String, request: MediaSessionRequest): MediaSession {
        return client.post("/api/v1/media/sessions") {
            bearerAuth(token)
            setBody(request)
        }.body()
    }

    suspend fun startLive(token: String, request: LiveStartRequest): LiveSessionState {
        return client.post("/api/v1/live/start") {
            bearerAuth(token)
            setBody(request)
        }.body()
    }

    suspend fun stopLive(token: String, sessionId: String): LiveSessionState {
        return client.post("/api/v1/live/stop") {
            bearerAuth(token)
            setBody(LiveStopRequest(sessionId))
        }.body()
    }

    override fun close() {
        client.close()
    }
}
