package com.lanshare.core.sync

import com.lanshare.core.api.model.SyncAction
import com.lanshare.core.api.model.SyncPairRequest
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncCoordinatorTest {
    @Test
    fun `scan detects copy_to_client for host-only file`() {
        val hostDir = createTempDirectory("sync-host-")
        val clientDir = createTempDirectory("sync-client-")
        Files.writeString(hostDir.resolve("doc.txt"), "ciao")

        val sync = SyncCoordinator()
        val pair = sync.createPair(
            SyncPairRequest(
                hostPath = hostDir.toString(),
                clientDeviceId = "client-1",
                clientPath = clientDir.toString()
            )
        )

        val result = sync.scan(pair.pairId)
        assertEquals(1, result.deltas.size)
        assertEquals(SyncAction.COPY_TO_CLIENT, result.deltas.first().action)
    }

    @Test
    fun `scan detects conflict for near-simultaneous updates`() {
        val hostDir = createTempDirectory("sync-host-")
        val clientDir = createTempDirectory("sync-client-")

        val hostFile = hostDir.resolve("shared/video.txt")
        val clientFile = clientDir.resolve("shared/video.txt")
        Files.createDirectories(hostFile.parent)
        Files.createDirectories(clientFile.parent)

        Files.writeString(hostFile, "host-version")
        Files.writeString(clientFile, "client-version")

        val time = FileTime.fromMillis(System.currentTimeMillis())
        Files.setLastModifiedTime(hostFile, time)
        Files.setLastModifiedTime(clientFile, time)

        val sync = SyncCoordinator()
        val pair = sync.createPair(
            SyncPairRequest(
                hostPath = hostDir.toString(),
                clientDeviceId = "client-7",
                clientPath = clientDir.toString()
            )
        )

        val result = sync.scan(pair.pairId)

        assertTrue(result.deltas.any { it.action == SyncAction.CONFLICT })
        assertTrue(result.conflicts.isNotEmpty())
        assertTrue(result.conflicts.first().conflictCopyName.contains("conflict-client-7"))
    }
}
