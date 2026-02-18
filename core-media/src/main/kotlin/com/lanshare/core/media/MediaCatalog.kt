package com.lanshare.core.media

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MediaCatalog {
    private val mediaById = ConcurrentHashMap<String, Path>()

    fun register(path: Path): String {
        require(Files.exists(path)) { "Media file does not exist: $path" }
        require(Files.isRegularFile(path)) { "Media path is not a file: $path" }

        val mediaId = UUID.randomUUID().toString()
        mediaById[mediaId] = path.toAbsolutePath().normalize()
        return mediaId
    }

    fun resolve(mediaId: String): Path? = mediaById[mediaId]

    fun allMedia(): Map<String, String> = mediaById.mapValues { (_, path) -> path.toString() }
}
