package com.lanshare.core.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val DEFAULT_CHUNK_SIZE_BYTES: Int = 4 * 1024 * 1024

@Serializable
enum class DeviceRole {
    HOST,
    CLIENT
}

@Serializable
enum class DeviceStatus {
    ONLINE,
    OFFLINE,
    BUSY
}

@Serializable
data class CapabilitySet(
    val fileTransfer: Boolean = true,
    val folderSync: Boolean = true,
    val mediaPlayback: Boolean = true,
    val liveStream: Boolean = true,
    val systemAudioCapture: Boolean = true
)

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val role: DeviceRole,
    val status: DeviceStatus,
    val ipAddress: String,
    val connectedAtEpochMillis: Long,
    val capabilities: CapabilitySet
)

@Serializable
data class HostAnnouncement(
    val hostId: String,
    val name: String,
    val apiPort: Int,
    val version: String,
    val fingerprintShort: String,
    val address: String? = null
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
    val hostFingerprint: String,
    val capabilities: CapabilitySet
)

@Serializable
data class AuthToken(
    val token: String
)

@Serializable
data class CreateTransferRequest(
    val fileName: String,
    val size: Long,
    val sha256: String,
    val chunkSize: Int = DEFAULT_CHUNK_SIZE_BYTES
)

@Serializable
data class TransferManifest(
    val transferId: String,
    val fileName: String,
    val size: Long,
    val sha256: String,
    val chunkSize: Int = DEFAULT_CHUNK_SIZE_BYTES,
    val createdAtEpochMillis: Long
)

@Serializable
data class ChunkAck(
    val transferId: String,
    val index: Int,
    val accepted: Boolean,
    val chunkSha256: String,
    val reason: String? = null
)

@Serializable
data class TransferProgress(
    val transferId: String,
    val bytesReceived: Long,
    val totalBytes: Long,
    val chunkIndex: Int,
    val completedChunks: Int,
    val totalChunks: Int
)

@Serializable
data class TransferResume(
    val transferId: String,
    val chunksPresent: List<Boolean>
)

@Serializable
data class CompleteTransferResponse(
    val transferId: String,
    val success: Boolean,
    val outputPath: String? = null,
    val message: String? = null
)

@Serializable
data class SyncPairRequest(
    val hostPath: String,
    val clientDeviceId: String,
    val clientPath: String
)

@Serializable
data class SyncPair(
    val pairId: String,
    val hostPath: String,
    val clientDeviceId: String,
    val clientPath: String,
    val active: Boolean,
    val createdAtEpochMillis: Long
)

@Serializable
enum class SyncAction {
    COPY_TO_CLIENT,
    COPY_TO_HOST,
    DELETE_ON_CLIENT,
    DELETE_ON_HOST,
    CONFLICT
}

@Serializable
data class SyncDelta(
    val pairId: String,
    val relativePath: String,
    val action: SyncAction,
    val hostModifiedEpochMillis: Long? = null,
    val clientModifiedEpochMillis: Long? = null,
    val reason: String? = null
)

@Serializable
data class ConflictRecord(
    val pairId: String,
    val relativePath: String,
    val hostDeviceId: String,
    val clientDeviceId: String,
    val conflictCopyName: String,
    val detectedAtEpochMillis: Long
)

@Serializable
data class SyncScanRequest(
    val pairId: String? = null
)

@Serializable
data class SyncScanResponse(
    val deltas: List<SyncDelta>,
    val conflicts: List<ConflictRecord>
)

@Serializable
enum class PlaybackMode {
    HOST_SYNC,
    INDEPENDENT
}

@Serializable
data class MediaRegisterRequest(
    val path: String
)

@Serializable
data class MediaRegisterResponse(
    val mediaId: String,
    val path: String
)

@Serializable
data class MediaSessionRequest(
    val mediaId: String,
    val mode: PlaybackMode,
    val hostDeviceId: String
)

@Serializable
data class MediaSession(
    val sessionId: String,
    val mediaId: String,
    val mode: PlaybackMode,
    val hostDeviceId: String,
    val createdAtEpochMillis: Long,
    val active: Boolean
)

@Serializable
enum class PlaybackCommandType {
    PLAY,
    PAUSE,
    SEEK,
    STOP
}

@Serializable
data class PlaybackCommand(
    val sessionId: String,
    val command: PlaybackCommandType,
    val positionMillis: Long,
    val issuedByDeviceId: String
)

@Serializable
data class PlaybackState(
    val sessionId: String,
    val positionMillis: Long,
    val playing: Boolean,
    val lastUpdateEpochMillis: Long,
    val driftCorrectionApplied: Boolean = false
)

@Serializable
data class LiveStartRequest(
    val hostDeviceId: String,
    val includeSystemAudio: Boolean = true,
    val targetWidth: Int = 1280,
    val targetHeight: Int = 720,
    val targetFps: Int = 30,
    val maxViewers: Int = 4
)

@Serializable
data class LiveStopRequest(
    val sessionId: String
)

@Serializable
enum class LiveStatus {
    IDLE,
    STARTING,
    RUNNING,
    STOPPED,
    FAILED
}

@Serializable
data class LiveSessionState(
    val sessionId: String,
    val hostDeviceId: String,
    val status: LiveStatus,
    val streamUrl: String? = null,
    val includeSystemAudio: Boolean = true,
    val viewerCount: Int = 0,
    val targetWidth: Int = 1280,
    val targetHeight: Int = 720,
    val targetFps: Int = 30,
    val errorMessage: String? = null
)

@Serializable
data class LiveQualityMetrics(
    val sessionId: String,
    val fps: Double,
    val estimatedLatencyMillis: Long,
    val droppedFrames: Long,
    val bitrateKbps: Long,
    val sampledAtEpochMillis: Long
)

@Serializable
enum class LanEventType {
    DEVICE_JOINED,
    DEVICE_LEFT,
    TRANSFER_PROGRESS,
    TRANSFER_COMPLETED,
    SYNC_CONFLICT,
    PLAYBACK_STATE,
    LIVE_STATE,
    LIVE_METRICS,
    ERROR
}

@Serializable
data class LanEvent(
    val type: LanEventType,
    val payload: JsonElement,
    val timestampEpochMillis: Long
)

@Serializable
data class UpdateCheckRequest(
    val currentVersion: String,
    val os: String,
    val arch: String
)

@Serializable
data class UpdateCheckResponse(
    val updateAvailable: Boolean,
    val latestVersion: String,
    val downloadUrl: String? = null,
    val signature: String? = null,
    val notes: String? = null
)

@Serializable
data class ErrorEnvelope(
    val code: String,
    val message: String,
    val details: String? = null,
    val retryable: Boolean = false
)
