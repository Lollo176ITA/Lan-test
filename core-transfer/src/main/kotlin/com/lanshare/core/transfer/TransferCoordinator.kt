package com.lanshare.core.transfer

import com.lanshare.core.api.model.ChunkAck
import com.lanshare.core.api.model.CompleteTransferResponse
import com.lanshare.core.api.model.CreateTransferRequest
import com.lanshare.core.api.model.TransferManifest
import com.lanshare.core.api.model.TransferProgress
import com.lanshare.core.api.model.TransferResume
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.Comparator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TransferCoordinator(
    storageRoot: Path
) {
    private val incomingRoot: Path = storageRoot.resolve("incoming")
    private val completedRoot: Path = storageRoot.resolve("completed")

    private val manifests = ConcurrentHashMap<String, TransferManifest>()
    private val chunkPresence = ConcurrentHashMap<String, MutableSet<Int>>()

    init {
        Files.createDirectories(incomingRoot)
        Files.createDirectories(completedRoot)
    }

    fun createTransfer(request: CreateTransferRequest): TransferManifest {
        require(request.size >= 0) { "size must be >= 0" }
        require(request.chunkSize > 0) { "chunkSize must be > 0" }

        val transferId = UUID.randomUUID().toString()
        val manifest = TransferManifest(
            transferId = transferId,
            fileName = request.fileName,
            size = request.size,
            sha256 = request.sha256.lowercase(),
            chunkSize = request.chunkSize,
            createdAtEpochMillis = Instant.now().toEpochMilli()
        )

        manifests[transferId] = manifest
        chunkPresence[transferId] = ConcurrentHashMap.newKeySet()

        Files.createDirectories(chunkDir(transferId))
        return manifest
    }

    fun uploadChunk(transferId: String, index: Int, chunkSha256: String, bytes: ByteArray): ChunkAck {
        val manifest = manifests[transferId]
            ?: return ChunkAck(transferId, index, false, chunkSha256, "transfer not found")

        val totalChunks = totalChunks(manifest)
        if (index < 0 || index >= totalChunks) {
            return ChunkAck(transferId, index, false, chunkSha256, "invalid chunk index")
        }

        val expectedLength = expectedChunkLength(manifest, index, totalChunks)
        if (bytes.size.toLong() != expectedLength) {
            return ChunkAck(
                transferId = transferId,
                index = index,
                accepted = false,
                chunkSha256 = chunkSha256,
                reason = "invalid chunk size, expected=$expectedLength actual=${bytes.size}"
            )
        }

        val actualHash = Hashing.sha256(bytes)
        if (actualHash != chunkSha256.lowercase()) {
            return ChunkAck(transferId, index, false, chunkSha256, "invalid chunk hash")
        }

        val out = chunkPath(transferId, index)
        Files.write(
            out,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )

        chunkPresence[transferId]?.add(index)
        return ChunkAck(transferId, index, true, actualHash)
    }

    fun downloadChunk(transferId: String, index: Int): ByteArray? {
        val manifest = manifests[transferId] ?: return null
        val totalChunks = totalChunks(manifest)
        if (index < 0 || index >= totalChunks) {
            return null
        }

        val path = chunkPath(transferId, index)
        if (!Files.exists(path)) {
            return null
        }
        return Files.readAllBytes(path)
    }

    fun transferProgress(transferId: String, lastChunkIndex: Int): TransferProgress? {
        val manifest = manifests[transferId] ?: return null
        val completedChunks = chunkPresence[transferId]?.size ?: 0
        val totalChunks = totalChunks(manifest)

        return TransferProgress(
            transferId = transferId,
            bytesReceived = minOf(manifest.size, completedChunks.toLong() * manifest.chunkSize),
            totalBytes = manifest.size,
            chunkIndex = lastChunkIndex,
            completedChunks = completedChunks,
            totalChunks = totalChunks
        )
    }

    fun resumeStatus(transferId: String): TransferResume? {
        val manifest = manifests[transferId] ?: return null
        val totalChunks = totalChunks(manifest)
        val present = chunkPresence[transferId].orEmpty()
        val bitmap = (0 until totalChunks).map { it in present }
        return TransferResume(transferId, bitmap)
    }

    fun completeTransfer(transferId: String): CompleteTransferResponse {
        val manifest = manifests[transferId]
            ?: return CompleteTransferResponse(transferId, false, message = "transfer not found")

        val totalChunks = totalChunks(manifest)
        val present = chunkPresence[transferId].orEmpty()
        if (manifest.size == 0L && present.isEmpty()) {
            val outputFile = completedRoot.resolve("${transferId}-${manifest.fileName}")
            Files.write(outputFile, byteArrayOf(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            val finalHash = Hashing.sha256(outputFile)
            if (finalHash != manifest.sha256.lowercase()) {
                Files.deleteIfExists(outputFile)
                return CompleteTransferResponse(transferId, false, message = "final hash mismatch")
            }
            clearTransfer(transferId)
            return CompleteTransferResponse(transferId, true, outputFile.toAbsolutePath().toString())
        }

        if (present.size != totalChunks) {
            return CompleteTransferResponse(
                transferId,
                false,
                message = "transfer incomplete (${present.size}/$totalChunks chunks)"
            )
        }

        val outputFile = completedRoot.resolve("${transferId}-${manifest.fileName}")
        try {
            Files.newOutputStream(
                outputFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            ).use { output ->
                for (index in 0 until totalChunks) {
                    val chunk = chunkPath(transferId, index)
                    Files.newInputStream(chunk).use { input ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (exception: IOException) {
            return CompleteTransferResponse(
                transferId,
                false,
                message = "failed to merge chunks: ${exception.message}"
            )
        }

        val finalHash = Hashing.sha256(outputFile)
        if (finalHash != manifest.sha256.lowercase()) {
            Files.deleteIfExists(outputFile)
            return CompleteTransferResponse(transferId, false, message = "final hash mismatch")
        }

        clearTransfer(transferId)
        return CompleteTransferResponse(transferId, true, outputFile.toAbsolutePath().toString())
    }

    fun cancelTransfer(transferId: String) {
        clearTransfer(transferId)
    }

    fun ingestExistingFile(path: Path, chunkSize: Int): TransferManifest {
        require(Files.exists(path)) { "File does not exist: $path" }
        val size = Files.size(path)
        val hash = Hashing.sha256(path)
        val manifest = createTransfer(
            CreateTransferRequest(
                fileName = path.fileName.toString(),
                size = size,
                sha256 = hash,
                chunkSize = chunkSize
            )
        )

        val buffer = ByteArray(chunkSize)
        var index = 0
        Files.newInputStream(path).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                val chunkHash = Hashing.sha256(chunk)
                uploadChunk(manifest.transferId, index, chunkHash, chunk)
                index++
            }
        }

        return manifest
    }

    private fun clearTransfer(transferId: String) {
        manifests.remove(transferId)
        chunkPresence.remove(transferId)

        val dir = chunkDir(transferId)
        if (Files.exists(dir)) {
            Files.walk(dir).use { stream ->
                stream
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun chunkDir(transferId: String): Path = incomingRoot.resolve(transferId)

    private fun chunkPath(transferId: String, index: Int): Path = chunkDir(transferId).resolve("$index.part")

    private fun totalChunks(manifest: TransferManifest): Int {
        if (manifest.size == 0L) {
            return 1
        }
        return ((manifest.size + manifest.chunkSize - 1) / manifest.chunkSize).toInt()
    }

    private fun expectedChunkLength(manifest: TransferManifest, index: Int, totalChunks: Int): Long {
        val regular = manifest.chunkSize.toLong()
        if (index < totalChunks - 1) {
            return regular
        }

        val usedByPrevious = (totalChunks - 1).toLong() * regular
        return manifest.size - usedByPrevious
    }
}
