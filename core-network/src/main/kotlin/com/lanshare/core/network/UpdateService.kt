package com.lanshare.core.network

import com.lanshare.core.api.model.UpdateCheckRequest
import com.lanshare.core.api.model.UpdateCheckResponse

class UpdateService(
    private val currentLatestVersion: String,
    private val downloadBaseUrl: String = "",
    private val signature: String = ""
) {
    fun check(request: UpdateCheckRequest): UpdateCheckResponse {
        val updateAvailable = compareSemver(currentLatestVersion, request.currentVersion) > 0
        if (!updateAvailable) {
            return UpdateCheckResponse(
                updateAvailable = false,
                latestVersion = currentLatestVersion,
                notes = "Nessun aggiornamento disponibile"
            )
        }

        val artifactName = "LanShare-${currentLatestVersion}-${request.os}-${request.arch}.zip"
        val downloadUrl = if (downloadBaseUrl.isBlank()) null else "$downloadBaseUrl/$artifactName"

        return UpdateCheckResponse(
            updateAvailable = true,
            latestVersion = currentLatestVersion,
            downloadUrl = downloadUrl,
            signature = signature.ifBlank { null },
            notes = "Aggiornamento disponibile"
        )
    }

    private fun compareSemver(left: String, right: String): Int {
        val l = left.split('.').map { it.toIntOrNull() ?: 0 }
        val r = right.split('.').map { it.toIntOrNull() ?: 0 }
        val max = maxOf(l.size, r.size)

        for (index in 0 until max) {
            val lv = l.getOrElse(index) { 0 }
            val rv = r.getOrElse(index) { 0 }
            if (lv != rv) {
                return lv.compareTo(rv)
            }
        }
        return 0
    }
}
