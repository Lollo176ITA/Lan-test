package com.lanshare.core.media

import com.lanshare.core.api.model.MediaSession
import com.lanshare.core.api.model.MediaSessionRequest
import com.lanshare.core.api.model.PlaybackCommand
import com.lanshare.core.api.model.PlaybackCommandType
import com.lanshare.core.api.model.PlaybackState
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MediaSessionCoordinator {
    private val sessions = ConcurrentHashMap<String, MediaSession>()
    private val playback = ConcurrentHashMap<String, PlaybackState>()

    fun createSession(request: MediaSessionRequest): MediaSession {
        val sessionId = UUID.randomUUID().toString()
        val now = Instant.now().toEpochMilli()

        val session = MediaSession(
            sessionId = sessionId,
            mediaId = request.mediaId,
            mode = request.mode,
            hostDeviceId = request.hostDeviceId,
            createdAtEpochMillis = now,
            active = true
        )
        sessions[sessionId] = session
        playback[sessionId] = PlaybackState(
            sessionId = sessionId,
            positionMillis = 0,
            playing = false,
            lastUpdateEpochMillis = now,
            driftCorrectionApplied = false
        )

        return session
    }

    fun applyCommand(command: PlaybackCommand): PlaybackState? {
        val current = playback[command.sessionId] ?: return null
        val now = Instant.now().toEpochMilli()

        val next = when (command.command) {
            PlaybackCommandType.PLAY -> current.copy(
                playing = true,
                positionMillis = command.positionMillis,
                lastUpdateEpochMillis = now,
                driftCorrectionApplied = false
            )

            PlaybackCommandType.PAUSE -> current.copy(
                playing = false,
                positionMillis = command.positionMillis,
                lastUpdateEpochMillis = now,
                driftCorrectionApplied = false
            )

            PlaybackCommandType.SEEK -> {
                val drift = kotlin.math.abs(current.positionMillis - command.positionMillis)
                current.copy(
                    positionMillis = command.positionMillis,
                    lastUpdateEpochMillis = now,
                    driftCorrectionApplied = drift > 250
                )
            }

            PlaybackCommandType.STOP -> current.copy(
                playing = false,
                positionMillis = 0,
                lastUpdateEpochMillis = now,
                driftCorrectionApplied = false
            )
        }

        playback[command.sessionId] = next
        return next
    }

    fun playbackState(sessionId: String): PlaybackState? = playback[sessionId]

    fun closeSession(sessionId: String) {
        sessions.remove(sessionId)
        playback.remove(sessionId)
    }
}
