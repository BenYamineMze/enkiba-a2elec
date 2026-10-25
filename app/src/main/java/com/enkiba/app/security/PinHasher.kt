package com.enkiba.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hachage du code PIN (PBKDF2, sel aléatoire). Le code lui-même n'est jamais enregistré.
 * HmacSHA1 : seule variante disponible dès Android 7 (minSdk 24).
 */
object PinHasher {

    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.hexToBytes(), ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded.toHex()
        } finally {
            spec.clearPassword()
        }
    }

    /** Comparaison en temps constant. */
    fun matches(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
