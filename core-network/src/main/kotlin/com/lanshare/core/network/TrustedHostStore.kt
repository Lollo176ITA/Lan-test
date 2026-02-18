package com.lanshare.core.network

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

data class TrustedHost(
    val hostId: String,
    val fingerprint: String,
    val firstSeenEpochMillis: Long
)

class TrustedHostStore(
    dataDir: Path
) {
    private val dbPath = dataDir.resolve("lanshare.db")
    private val jdbcUrl = "jdbc:sqlite:$dbPath"

    init {
        Files.createDirectories(dataDir)
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS trusted_hosts (
                        host_id TEXT PRIMARY KEY,
                        fingerprint TEXT NOT NULL,
                        first_seen_epoch_millis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    fun save(hostId: String, fingerprint: String) {
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO trusted_hosts(host_id, fingerprint, first_seen_epoch_millis)
                VALUES (?, ?, ?)
                ON CONFLICT(host_id)
                DO UPDATE SET fingerprint = excluded.fingerprint
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, hostId)
                stmt.setString(2, fingerprint)
                stmt.setLong(3, System.currentTimeMillis())
                stmt.executeUpdate()
            }
        }
    }

    fun get(hostId: String): TrustedHost? {
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.prepareStatement(
                """
                SELECT host_id, fingerprint, first_seen_epoch_millis
                FROM trusted_hosts
                WHERE host_id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, hostId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return TrustedHost(
                        hostId = rs.getString("host_id"),
                        fingerprint = rs.getString("fingerprint"),
                        firstSeenEpochMillis = rs.getLong("first_seen_epoch_millis")
                    )
                }
            }
        }
        return null
    }
}
