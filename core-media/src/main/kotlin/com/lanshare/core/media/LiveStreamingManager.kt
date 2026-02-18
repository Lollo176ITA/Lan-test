package com.lanshare.core.media

import com.lanshare.core.api.model.LiveQualityMetrics
import com.lanshare.core.api.model.LiveSessionState
import com.lanshare.core.api.model.LiveStartRequest
import com.lanshare.core.api.model.LiveStatus
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LiveStreamingManager(
    private val ffmpegExecutable: Path,
    private val bindHost: String,
    private val bindPort: Int
) {
    private val sessions = ConcurrentHashMap<String, LiveSessionState>()
    private val metricsBySession = ConcurrentHashMap<String, LiveQualityMetrics>()

    @Synchronized
    fun start(request: LiveStartRequest): LiveSessionState {
        val runningSession = sessions.values.firstOrNull {
            it.status == LiveStatus.RUNNING || it.status == LiveStatus.STARTING
        }
        if (runningSession != null) {
            return LiveSessionState(
                sessionId = runningSession.sessionId,
                hostDeviceId = request.hostDeviceId,
                status = LiveStatus.FAILED,
                includeSystemAudio = request.includeSystemAudio,
                targetWidth = request.targetWidth,
                targetHeight = request.targetHeight,
                targetFps = request.targetFps,
                errorMessage = "Esiste gia una sessione live attiva sull'host"
            )
        }

        if (!Files.exists(ffmpegExecutable)) {
            return LiveSessionState(
                sessionId = UUID.randomUUID().toString(),
                hostDeviceId = request.hostDeviceId,
                status = LiveStatus.FAILED,
                includeSystemAudio = request.includeSystemAudio,
                targetWidth = request.targetWidth,
                targetHeight = request.targetHeight,
                targetFps = request.targetFps,
                errorMessage = "FFmpeg non trovato: $ffmpegExecutable"
            )
        }

        if (request.includeSystemAudio && !supportsSystemAudioCapture()) {
            return LiveSessionState(
                sessionId = UUID.randomUUID().toString(),
                hostDeviceId = request.hostDeviceId,
                status = LiveStatus.FAILED,
                includeSystemAudio = true,
                targetWidth = request.targetWidth,
                targetHeight = request.targetHeight,
                targetFps = request.targetFps,
                errorMessage = "Audio di sistema non disponibile su questo host. Abilitare bridge loopback o impostare LANSHARE_FORCE_SYSTEM_AUDIO=true"
            )
        }

        val sessionId = UUID.randomUUID().toString()
        val streamPath = "live/$sessionId/index.m3u8"
        val streamUrl = "https://$bindHost:$bindPort/$streamPath"

        val state = LiveSessionState(
            sessionId = sessionId,
            hostDeviceId = request.hostDeviceId,
            status = LiveStatus.RUNNING,
            streamUrl = streamUrl,
            includeSystemAudio = request.includeSystemAudio,
            viewerCount = 0,
            targetWidth = request.targetWidth,
            targetHeight = request.targetHeight,
            targetFps = request.targetFps,
            errorMessage = null
        )

        sessions[sessionId] = state
        metricsBySession[sessionId] = LiveQualityMetrics(
            sessionId = sessionId,
            fps = request.targetFps.toDouble(),
            estimatedLatencyMillis = 220,
            droppedFrames = 0,
            bitrateKbps = 3_500,
            sampledAtEpochMillis = Instant.now().toEpochMilli()
        )
        return state
    }

    fun stop(sessionId: String): LiveSessionState? {
        val current = sessions[sessionId] ?: return null
        val stopped = current.copy(status = LiveStatus.STOPPED, streamUrl = null)
        sessions[sessionId] = stopped
        return stopped
    }

    fun state(sessionId: String): LiveSessionState? = sessions[sessionId]

    fun metrics(sessionId: String): LiveQualityMetrics? = metricsBySession[sessionId]

    fun supportsSystemAudioCapture(osName: String = System.getProperty("os.name")): Boolean {
        if (System.getenv("LANSHARE_FORCE_SYSTEM_AUDIO") == "true") {
            return true
        }

        return when {
            osName.contains("mac", ignoreCase = true) -> false
            osName.contains("win", ignoreCase = true) -> true
            osName.contains("linux", ignoreCase = true) -> true
            else -> false
        }
    }

    fun ffmpegCommandPreview(sessionId: String, request: LiveStartRequest): List<String> {
        val output = "live/$sessionId/index.m3u8"
        val os = System.getProperty("os.name").lowercase()
        val command = mutableListOf(
            ffmpegExecutable.toAbsolutePath().toString(),
            "-framerate", request.targetFps.toString()
        )

        when {
            os.contains("mac") -> {
                command += listOf("-f", "avfoundation", "-i", "1")
                if (request.includeSystemAudio) {
                    command += listOf("-f", "avfoundation", "-i", ":0")
                }
            }

            os.contains("win") -> {
                command += listOf("-f", "gdigrab", "-i", "desktop")
                if (request.includeSystemAudio) {
                    command += listOf("-f", "dshow", "-i", "audio=virtual-audio-capturer")
                }
            }

            else -> {
                command += listOf("-f", "x11grab", "-i", ":0.0")
                if (request.includeSystemAudio) {
                    command += listOf("-f", "pulse", "-i", "default")
                }
            }
        }

        command += listOf(
            "-vf", "scale=${request.targetWidth}:${request.targetHeight}",
            "-preset", "veryfast",
            "-tune", "zerolatency",
            "-f", "hls",
            output
        )
        return command
    }
}
