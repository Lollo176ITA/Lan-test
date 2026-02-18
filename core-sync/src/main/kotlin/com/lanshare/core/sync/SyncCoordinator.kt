package com.lanshare.core.sync

import com.lanshare.core.api.model.ConflictRecord
import com.lanshare.core.api.model.SyncAction
import com.lanshare.core.api.model.SyncDelta
import com.lanshare.core.api.model.SyncPair
import com.lanshare.core.api.model.SyncPairRequest
import com.lanshare.core.api.model.SyncScanResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class SyncCoordinator {
    private val pairs = ConcurrentHashMap<String, SyncPair>()

    fun createPair(request: SyncPairRequest): SyncPair {
        val hostPath = Path.of(request.hostPath).toAbsolutePath().normalize()
        val clientPath = Path.of(request.clientPath).toAbsolutePath().normalize()

        Files.createDirectories(hostPath)
        Files.createDirectories(clientPath)

        val pair = SyncPair(
            pairId = UUID.randomUUID().toString(),
            hostPath = hostPath.toString(),
            clientDeviceId = request.clientDeviceId,
            clientPath = clientPath.toString(),
            active = true,
            createdAtEpochMillis = Instant.now().toEpochMilli()
        )
        pairs[pair.pairId] = pair
        return pair
    }

    fun listPairs(): List<SyncPair> = pairs.values.sortedBy { it.createdAtEpochMillis }

    fun scan(pairId: String? = null, hostDeviceId: String = "host"): SyncScanResponse {
        val targets = if (pairId == null) {
            listPairs()
        } else {
            listOfNotNull(pairs[pairId])
        }

        val allDeltas = mutableListOf<SyncDelta>()
        val conflicts = mutableListOf<ConflictRecord>()

        for (pair in targets) {
            val hostRoot = Path.of(pair.hostPath)
            val clientRoot = Path.of(pair.clientPath)

            val hostIndex = buildIndex(hostRoot)
            val clientIndex = buildIndex(clientRoot)

            val allPaths = (hostIndex.keys + clientIndex.keys).toSortedSet()
            for (relative in allPaths) {
                val hostEntry = hostIndex[relative]
                val clientEntry = clientIndex[relative]

                when {
                    hostEntry != null && clientEntry == null -> {
                        allDeltas += SyncDelta(
                            pairId = pair.pairId,
                            relativePath = relative,
                            action = SyncAction.COPY_TO_CLIENT,
                            hostModifiedEpochMillis = hostEntry.lastModified,
                            reason = "missing on client"
                        )
                    }

                    hostEntry == null && clientEntry != null -> {
                        allDeltas += SyncDelta(
                            pairId = pair.pairId,
                            relativePath = relative,
                            action = SyncAction.COPY_TO_HOST,
                            clientModifiedEpochMillis = clientEntry.lastModified,
                            reason = "missing on host"
                        )
                    }

                    hostEntry != null && clientEntry != null -> {
                        if (hostEntry.fingerprint == clientEntry.fingerprint) {
                            continue
                        }

                        val diff = hostEntry.lastModified - clientEntry.lastModified
                        when {
                            diff > CONFLICT_WINDOW_MILLIS -> {
                                allDeltas += SyncDelta(
                                    pairId = pair.pairId,
                                    relativePath = relative,
                                    action = SyncAction.COPY_TO_CLIENT,
                                    hostModifiedEpochMillis = hostEntry.lastModified,
                                    clientModifiedEpochMillis = clientEntry.lastModified,
                                    reason = "host newer"
                                )
                            }

                            diff < -CONFLICT_WINDOW_MILLIS -> {
                                allDeltas += SyncDelta(
                                    pairId = pair.pairId,
                                    relativePath = relative,
                                    action = SyncAction.COPY_TO_HOST,
                                    hostModifiedEpochMillis = hostEntry.lastModified,
                                    clientModifiedEpochMillis = clientEntry.lastModified,
                                    reason = "client newer"
                                )
                            }

                            else -> {
                                val conflictCopy = buildConflictCopyName(relative, pair.clientDeviceId)
                                conflicts += ConflictRecord(
                                    pairId = pair.pairId,
                                    relativePath = relative,
                                    hostDeviceId = hostDeviceId,
                                    clientDeviceId = pair.clientDeviceId,
                                    conflictCopyName = conflictCopy,
                                    detectedAtEpochMillis = Instant.now().toEpochMilli()
                                )
                                allDeltas += SyncDelta(
                                    pairId = pair.pairId,
                                    relativePath = relative,
                                    action = SyncAction.CONFLICT,
                                    hostModifiedEpochMillis = hostEntry.lastModified,
                                    clientModifiedEpochMillis = clientEntry.lastModified,
                                    reason = "simultaneous update"
                                )
                            }
                        }
                    }
                }
            }
        }

        return SyncScanResponse(allDeltas, conflicts)
    }

    fun applyDelta(delta: SyncDelta): Boolean {
        val pair = pairs[delta.pairId] ?: return false
        val hostRoot = Path.of(pair.hostPath)
        val clientRoot = Path.of(pair.clientPath)
        val hostPath = hostRoot.resolve(delta.relativePath)
        val clientPath = clientRoot.resolve(delta.relativePath)

        return when (delta.action) {
            SyncAction.COPY_TO_CLIENT -> {
                copy(hostPath, clientPath)
            }

            SyncAction.COPY_TO_HOST -> {
                copy(clientPath, hostPath)
            }

            SyncAction.DELETE_ON_CLIENT -> {
                Files.deleteIfExists(clientPath)
            }

            SyncAction.DELETE_ON_HOST -> {
                Files.deleteIfExists(hostPath)
            }

            SyncAction.CONFLICT -> {
                materializeConflictCopy(clientPath, pair.clientDeviceId)
            }
        }
    }

    private fun materializeConflictCopy(path: Path, clientDeviceId: String): Boolean {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return false
        }
        val conflictName = buildConflictCopyName(path.fileName.toString(), clientDeviceId)
        val conflictPath = path.resolveSibling(conflictName)
        Files.copy(path, conflictPath, StandardCopyOption.REPLACE_EXISTING)
        return true
    }

    private fun copy(source: Path, target: Path): Boolean {
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            return false
        }

        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
        return true
    }

    private fun buildIndex(root: Path): Map<String, FileEntry> {
        if (!Files.exists(root)) {
            return emptyMap()
        }

        val index = mutableMapOf<String, FileEntry>()
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .forEach { path ->
                    val relative = root.relativize(path).toString().replace("\\", "/")
                    val size = Files.size(path)
                    val modified = Files.getLastModifiedTime(path).toMillis()
                    index[relative] = FileEntry(
                        relativePath = relative,
                        size = size,
                        lastModified = modified,
                        fingerprint = "$size:$modified"
                    )
                }
        }
        return index
    }

    fun buildConflictCopyName(relativePath: String, deviceId: String): String {
        val path = Path.of(relativePath)
        val fileName = path.fileName.toString()
        val nameWithoutExt = path.fileName.nameWithoutExtension
        val ext = path.fileName.extension
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())

        val suffix = " (conflict-$deviceId-$timestamp)"
        return if (ext.isBlank() || nameWithoutExt == fileName) {
            "$fileName$suffix"
        } else {
            "$nameWithoutExt$suffix.$ext"
        }
    }

    private data class FileEntry(
        val relativePath: String,
        val size: Long,
        val lastModified: Long,
        val fingerprint: String
    )

    private companion object {
        const val CONFLICT_WINDOW_MILLIS: Long = 1_000
    }
}
