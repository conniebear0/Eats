package com.eats.app.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 password hashing. Passwords are never stored or logged, only the salt and the hash.
 * Slow on purpose (many iterations), so call it off the main thread.
 */
object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    fun newSalt(): String = Base64.encodeToString(randomBytes(SALT_BYTES), Base64.NO_WRAP)

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison, so response time does not leak how much of the hash matched. */
    fun verify(password: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(password, salt).toByteArray(), expectedHash.toByteArray())

    /** A random URL-safe token, used as the session token. */
    fun newToken(): String =
        Base64.encodeToString(randomBytes(32), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

    /** A random 6 digit code for password reset. */
    fun newCode(): String = "%06d".format(random.nextInt(1_000_000))

    private fun randomBytes(count: Int) = ByteArray(count).also(random::nextBytes)
}
