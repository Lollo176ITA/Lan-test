package com.lanshare.core.transfer

import com.lanshare.core.api.model.CreateTransferRequest
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransferCoordinatorTest {
    @Test
    fun `upload resume and complete transfer`() {
        val root = createTempDirectory("transfer-test-")
        val coordinator = TransferCoordinator(root)

        val payload = ByteArray(2_500_000) { (it % 255).toByte() }
        val hash = Hashing.sha256(payload)
        val chunkSize = 1_000_000

        val manifest = coordinator.createTransfer(
            CreateTransferRequest(
                fileName = "video.bin",
                size = payload.size.toLong(),
                sha256 = hash,
                chunkSize = chunkSize
            )
        )

        val totalChunks = ((payload.size + chunkSize - 1) / chunkSize)
        for (index in 0 until totalChunks) {
            val start = index * chunkSize
            val end = minOf(payload.size, start + chunkSize)
            val chunk = payload.copyOfRange(start, end)
            val ack = coordinator.uploadChunk(
                manifest.transferId,
                index,
                Hashing.sha256(chunk),
                chunk
            )
            assertTrue(ack.accepted)
        }

        val resume = assertNotNull(coordinator.resumeStatus(manifest.transferId))
        assertEquals(totalChunks, resume.chunksPresent.size)
        assertTrue(resume.chunksPresent.all { it })

        val complete = coordinator.completeTransfer(manifest.transferId)
        assertTrue(complete.success)
        assertTrue(!complete.outputPath.isNullOrBlank())

        val output = Files.readAllBytes(java.nio.file.Path.of(complete.outputPath!!))
        assertEquals(hash, Hashing.sha256(output))
    }
}
