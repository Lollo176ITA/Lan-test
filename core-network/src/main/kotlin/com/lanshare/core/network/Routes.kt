package com.lanshare.core.network

import com.lanshare.core.api.model.CompleteTransferResponse
import com.lanshare.core.api.model.CreateTransferRequest
import com.lanshare.core.api.model.ErrorEnvelope
import com.lanshare.core.api.model.JoinRequest
import com.lanshare.core.api.model.JoinResponse
import com.lanshare.core.api.model.LanEventType
import com.lanshare.core.api.model.LiveStartRequest
import com.lanshare.core.api.model.LiveStatus
import com.lanshare.core.api.model.LiveStopRequest
import com.lanshare.core.api.model.MediaRegisterRequest
import com.lanshare.core.api.model.MediaRegisterResponse
import com.lanshare.core.api.model.MediaSessionRequest
import com.lanshare.core.api.model.PlaybackCommand
import com.lanshare.core.api.model.SyncPairRequest
import com.lanshare.core.api.model.SyncScanRequest
import com.lanshare.core.api.model.UpdateCheckRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.flow.collect
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.min

fun Application.installLanShareModule(context: ServerContext) {
    install(CallLogging)
    install(ContentNegotiation) {
        json(context.json)
    }
    install(WebSockets)

    routing {
        get("/health") {
            call.respondText("ok")
        }

        route("/api/v1") {
            post("/join") {
                val request = call.receiveNullable<JoinRequest>()
                if (request == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("JOIN_BAD_REQUEST", "Body join non valido", retryable = false)
                    )
                    return@post
                }

                if (request.hostId != context.config.hostId) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("JOIN_HOST_MISMATCH", "HostId non valido", retryable = false)
                    )
                    return@post
                }

                if (request.pin.length != 6 || request.pin.any { !it.isDigit() }) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("JOIN_PIN_FORMAT", "PIN deve avere 6 cifre", retryable = true)
                    )
                    return@post
                }

                if (!context.pinManager.validate(request.pin)) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorEnvelope("JOIN_INVALID_PIN", "PIN non valido", retryable = true)
                    )
                    return@post
                }

                val clientIp = call.request.local.remoteHost
                val (token, device) = context.deviceRegistry.registerClient(request.deviceName, clientIp)
                val response = JoinResponse(
                    sessionToken = token,
                    hostFingerprint = context.tlsMaterial.fingerprint,
                    capabilities = device.capabilities
                )

                context.eventHub.emit(LanEventType.DEVICE_JOINED, device)
                call.respond(HttpStatusCode.Created, response)
            }

            get("/devices") {
                if (!call.ensureAuthorized(context)) {
                    return@get
                }
                call.respond(context.deviceRegistry.listDevices())
            }

            webSocket("/events") {
                val token = call.request.queryParameters["token"]
                if (!context.deviceRegistry.isValidToken(token)) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                    return@webSocket
                }

                context.eventHub.events.collect { event ->
                    send(Frame.Text(context.eventHub.serialize(event)))
                }
            }

            post("/transfers") {
                if (!call.ensureAuthorized(context)) {
                    return@post
                }
                val request = call.receiveNullable<CreateTransferRequest>()
                if (request == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("TRANSFER_BAD_REQUEST", "Manifest non valido", retryable = false)
                    )
                    return@post
                }

                val manifest = context.transferCoordinator.createTransfer(request)
                call.respond(HttpStatusCode.Created, manifest)
            }

            put("/transfers/{id}/chunks/{index}") {
                if (!call.ensureAuthorized(context)) {
                    return@put
                }

                val transferId = call.parameters["id"]
                val index = call.parameters["index"]?.toIntOrNull()
                val chunkHash = call.request.headers["X-Chunk-Sha256"]

                if (transferId == null || index == null || chunkHash.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("CHUNK_BAD_REQUEST", "Parametri chunk mancanti", retryable = true)
                    )
                    return@put
                }

                val bytes = call.receive<ByteArray>()
                val ack = context.transferCoordinator.uploadChunk(transferId, index, chunkHash, bytes)
                if (!ack.accepted) {
                    call.respond(HttpStatusCode.BadRequest, ack)
                    return@put
                }

                val progress = context.transferCoordinator.transferProgress(transferId, index)
                if (progress != null) {
                    context.eventHub.emit(LanEventType.TRANSFER_PROGRESS, progress)
                }

                call.respond(HttpStatusCode.Accepted, ack)
            }

            get("/transfers/{id}/chunks/{index}") {
                if (!call.ensureAuthorized(context)) {
                    return@get
                }

                val transferId = call.parameters["id"]
                val index = call.parameters["index"]?.toIntOrNull()
                if (transferId == null || index == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("CHUNK_BAD_REQUEST", "Parametri chunk mancanti", retryable = true)
                    )
                    return@get
                }

                val chunk = context.transferCoordinator.downloadChunk(transferId, index)
                if (chunk == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorEnvelope("CHUNK_NOT_FOUND", "Chunk non disponibile", retryable = true)
                    )
                    return@get
                }

                call.respondBytes(chunk, ContentType.Application.OctetStream)
            }

            get("/transfers/{id}/resume") {
                if (!call.ensureAuthorized(context)) {
                    return@get
                }
                val transferId = call.parameters["id"]
                if (transferId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("RESUME_BAD_REQUEST", "Transfer id mancante", retryable = false)
                    )
                    return@get
                }

                val resume = context.transferCoordinator.resumeStatus(transferId)
                if (resume == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorEnvelope("RESUME_NOT_FOUND", "Transfer non trovato", retryable = false)
                    )
                    return@get
                }

                call.respond(resume)
            }

            post("/transfers/{id}/complete") {
                if (!call.ensureAuthorized(context)) {
                    return@post
                }
                val transferId = call.parameters["id"]
                if (transferId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("COMPLETE_BAD_REQUEST", "Transfer id mancante", retryable = false)
                    )
                    return@post
                }

                val result: CompleteTransferResponse = context.transferCoordinator.completeTransfer(transferId)
                if (!result.success) {
                    call.respond(HttpStatusCode.BadRequest, result)
                    return@post
                }

                context.eventHub.emit(LanEventType.TRANSFER_COMPLETED, result)
                call.respond(result)
            }

            post("/sync/pairs") {
                if (!call.ensureAuthorized(context)) {
                    return@post
                }
                val request = call.receiveNullable<SyncPairRequest>()
                if (request == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("SYNC_PAIR_BAD_REQUEST", "Body pair non valido", retryable = false)
                    )
                    return@post
                }

                val pair = context.syncCoordinator.createPair(request)
                call.respond(HttpStatusCode.Created, pair)
            }

            post("/sync/scan") {
                if (!call.ensureAuthorized(context)) {
                    return@post
                }
                val request = call.receiveNullable<SyncScanRequest>() ?: SyncScanRequest()
                val result = context.syncCoordinator.scan(request.pairId)

                result.conflicts.forEach { conflict ->
                    context.eventHub.emit(LanEventType.SYNC_CONFLICT, conflict)
                }

                call.respond(result)
            }

            post("/media/register") {
                if (!call.ensureAuthorized(context)) {
                    return@post
                }

                val request = call.receiveNullable<MediaRegisterRequest>()
                if (request == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("MEDIA_BAD_REQUEST", "Body media non valido", retryable = false)
                    )
                    return@post
                }

                val path = Path.of(request.path)
                if (!Files.exists(path)) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorEnvelope("MEDIA_NOT_FOUND", "File media non trovato", retryable = false)
                    )
                    return@post
                }

                val mediaId = context.mediaCatalog.register(path)
                call.respond(MediaRegisterResponse(mediaId, path.toString()))
            }

            get("/media/files/{mediaId}") {
                if (!call.ensureAuthorized(context)) {
                    return@get
                }

                val mediaId = call.parameters["mediaId"]
                if (mediaId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("MEDIA_BAD_REQUEST", "MediaId mancante", retryable = false)
                    )
                    return@get
                }

                val path = context.mediaCatalog.resolve(mediaId)
                if (path == null || !Files.exists(path)) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorEnvelope("MEDIA_NOT_FOUND", "Media non registrato", retryable = false)
                    )
                    return@get
                }

                call.respondFileWithRange(path)
            }

            post("/media/sessions") {
                if (!call.ensureAuthorized(context)) {
                    return@post
                }

                val request = call.receiveNullable<MediaSessionRequest>()
                if (request == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("MEDIA_SESSION_BAD_REQUEST", "Body session non valido", retryable = false)
                    )
                    return@post
                }

                if (context.mediaCatalog.resolve(request.mediaId) == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorEnvelope("MEDIA_NOT_FOUND", "Media non registrato", retryable = false)
                    )
                    return@post
                }

                val session = context.mediaSessionCoordinator.createSession(request)
                call.respond(HttpStatusCode.Created, session)
            }

            post("/media/playback") {
                if (!call.ensureAuthorized(context)) {
                    return@post
                }

                val command = call.receiveNullable<PlaybackCommand>()
                if (command == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("PLAYBACK_BAD_REQUEST", "Comando playback non valido", retryable = false)
                    )
                    return@post
                }

                val state = context.mediaSessionCoordinator.applyCommand(command)
                if (state == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorEnvelope("PLAYBACK_SESSION_NOT_FOUND", "Sessione playback non trovata", retryable = false)
                    )
                    return@post
                }

                context.eventHub.emit(LanEventType.PLAYBACK_STATE, state)
                call.respond(state)
            }

            post("/live/start") {
                if (!call.ensureAuthorized(context)) {
                    return@post
                }

                val request = call.receiveNullable<LiveStartRequest>()
                if (request == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("LIVE_BAD_REQUEST", "Body live non valido", retryable = false)
                    )
                    return@post
                }

                val state = context.liveStreamingManager.start(request)
                if (state.status == LiveStatus.FAILED) {
                    call.respond(HttpStatusCode.BadRequest, state)
                    return@post
                }

                context.eventHub.emit(LanEventType.LIVE_STATE, state)
                call.respond(HttpStatusCode.Accepted, state)
            }

            post("/live/stop") {
                if (!call.ensureAuthorized(context)) {
                    return@post
                }

                val request = call.receiveNullable<LiveStopRequest>()
                if (request == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorEnvelope("LIVE_BAD_REQUEST", "Body stop live non valido", retryable = false)
                    )
                    return@post
                }

                val stopped = context.liveStreamingManager.stop(request.sessionId)
                if (stopped == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorEnvelope("LIVE_NOT_FOUND", "Sessione live non trovata", retryable = false)
                    )
                    return@post
                }

                context.eventHub.emit(LanEventType.LIVE_STATE, stopped)
                call.respond(stopped)
            }

            get("/update/check") {
                val request = UpdateCheckRequest(
                    currentVersion = call.request.queryParameters["currentVersion"] ?: context.config.appVersion,
                    os = call.request.queryParameters["os"] ?: "unknown",
                    arch = call.request.queryParameters["arch"] ?: "unknown"
                )

                call.respond(context.updateService.check(request))
            }
        }
    }
}

private suspend fun ApplicationCall.ensureAuthorized(context: ServerContext): Boolean {
    val token = extractToken()
    if (context.deviceRegistry.isValidToken(token)) {
        return true
    }

    respond(
        HttpStatusCode.Unauthorized,
        ErrorEnvelope("UNAUTHORIZED", "Token sessione mancante o non valido", retryable = false)
    )
    return false
}

private fun ApplicationCall.extractToken(): String? {
    val header = request.headers[HttpHeaders.Authorization]
    if (header != null && header.startsWith("Bearer ")) {
        return header.removePrefix("Bearer ").trim()
    }
    return request.queryParameters["token"]
}

private suspend fun ApplicationCall.respondFileWithRange(path: Path) {
    val size = Files.size(path)
    if (size == 0L) {
        response.header(HttpHeaders.AcceptRanges, "bytes")
        respondFile(path.toFile())
        return
    }
    val range = request.headers[HttpHeaders.Range]

    if (range.isNullOrBlank()) {
        response.header(HttpHeaders.AcceptRanges, "bytes")
        respondFile(path.toFile())
        return
    }

    val parsed = parseRange(range, size)
    if (parsed == null) {
        respond(
            HttpStatusCode.RequestedRangeNotSatisfiable,
            ErrorEnvelope("INVALID_RANGE", "Header range non valido", retryable = true)
        )
        return
    }

    val (start, end) = parsed
    val length = end - start + 1

    response.header(HttpHeaders.AcceptRanges, "bytes")
    response.header(HttpHeaders.ContentRange, "bytes $start-$end/$size")

    respondOutputStream(
        status = HttpStatusCode.PartialContent,
        contentType = ContentType.Video.Any
    ) {
        Files.newInputStream(path).use { input ->
            input.skipFully(start)
            var remaining = length
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (remaining > 0) {
                val chunk = min(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, chunk)
                if (read < 0) {
                    break
                }
                write(buffer, 0, read)
                remaining -= read
            }
        }
    }
}

private fun parseRange(header: String, totalSize: Long): Pair<Long, Long>? {
    val match = RANGE_REGEX.matchEntire(header.trim()) ?: return null
    val startText = match.groupValues[1]
    val endText = match.groupValues[2]

    val start = if (startText.isBlank()) 0L else startText.toLongOrNull() ?: return null
    val end = if (endText.isBlank()) totalSize - 1 else endText.toLongOrNull() ?: return null

    if (start < 0 || end < start || end >= totalSize) {
        return null
    }

    return start to end
}

private fun InputStream.skipFully(bytes: Long) {
    var remaining = bytes
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped <= 0) {
            if (read() == -1) {
                break
            }
            remaining--
        } else {
            remaining -= skipped
        }
    }
}

private val RANGE_REGEX = Regex("bytes=(\\d*)-(\\d*)")
