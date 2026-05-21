package proj.memorchess.axl.core.auth

import java.security.MessageDigest
import java.security.SecureRandom

private val secureRandom = SecureRandom()

internal actual fun randomBytes(size: Int): ByteArray =
  ByteArray(size).also { secureRandom.nextBytes(it) }

internal actual suspend fun sha256(bytes: ByteArray): ByteArray =
  MessageDigest.getInstance("SHA-256").digest(bytes)
