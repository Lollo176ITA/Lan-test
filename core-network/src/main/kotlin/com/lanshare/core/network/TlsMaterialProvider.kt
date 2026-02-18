package com.lanshare.core.network

import io.ktor.network.tls.certificates.buildKeyStore
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest

data class TlsMaterial(
    val keyStore: KeyStore,
    val keyStorePath: Path,
    val alias: String,
    val keyStorePassword: String,
    val privateKeyPassword: String,
    val fingerprint: String
)

class TlsMaterialProvider(
    private val dataDir: Path,
    private val hostName: String
) {
    private val alias = "lanshare"
    private val keyStorePath = dataDir.resolve("lanshare-keystore.jks")
    private val secretPath = dataDir.resolve("lanshare-keystore.secret")

    fun loadOrCreate(): TlsMaterial {
        Files.createDirectories(dataDir)
        val secret = loadOrCreateSecret()
        val keyStore = if (Files.exists(keyStorePath)) {
            KeyStore.getInstance("JKS").apply {
                FileInputStream(keyStorePath.toFile()).use { input ->
                    load(input, secret.toCharArray())
                }
            }
        } else {
            val store = buildKeyStore {
                certificate(alias) {
                    password = secret
                    domains = listOf("localhost", "127.0.0.1", hostName)
                }
            }
            FileOutputStream(keyStorePath.toFile()).use { output ->
                store.store(output, secret.toCharArray())
            }
            store
        }

        val cert = keyStore.getCertificate(alias)
        val fingerprint = cert.encoded.sha256Hex()

        return TlsMaterial(
            keyStore = keyStore,
            keyStorePath = keyStorePath,
            alias = alias,
            keyStorePassword = secret,
            privateKeyPassword = secret,
            fingerprint = fingerprint
        )
    }

    private fun loadOrCreateSecret(): String {
        if (Files.exists(secretPath)) {
            return Files.readString(secretPath).trim()
        }

        val secret = MessageDigest.getInstance("SHA-256")
            .digest("$hostName-${System.nanoTime()}".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

        Files.writeString(secretPath, secret)
        return secret
    }

    private fun ByteArray.sha256Hex(): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(this)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
