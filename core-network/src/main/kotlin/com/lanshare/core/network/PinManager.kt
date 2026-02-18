package com.lanshare.core.network

import java.security.SecureRandom

class PinManager(
    private val random: SecureRandom = SecureRandom()
) {
    @Volatile
    private var pin: String = generatePinInternal()

    fun currentPin(): String = pin

    fun regeneratePin(): String {
        pin = generatePinInternal()
        return pin
    }

    fun validate(candidate: String): Boolean = candidate == pin

    private fun generatePinInternal(): String {
        val value = random.nextInt(1_000_000)
        return value.toString().padStart(6, '0')
    }
}
