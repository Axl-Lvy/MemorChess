package proj.memorchess.axl.core.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Generates an OAuth 2.0 PKCE (Proof Key for Code Exchange, RFC 7636) verifier and challenge pair.
 *
 * The verifier is a 64 byte cryptographically random value encoded as Base64 URL (no padding). The
 * challenge is `BASE64URL(SHA256(verifier))` per RFC 7636 section 4.2. Lichess requires `S256` as
 * the challenge method.
 */
object PkceGenerator {

  /** Returns a freshly generated verifier/challenge pair. */
  suspend fun generate(): PkcePair {
    val verifier = encodeBase64Url(randomBytes(VERIFIER_BYTE_LENGTH))
    val challenge = encodeBase64Url(sha256(verifier.encodeToByteArray()))
    return PkcePair(verifier = verifier, challenge = challenge)
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun encodeBase64Url(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')

  private const val VERIFIER_BYTE_LENGTH = 64
}

/** Verifier kept by the client and challenge sent to the authorization server. */
data class PkcePair(val verifier: String, val challenge: String)

/** Platform specific cryptographically secure random bytes. */
internal expect fun randomBytes(size: Int): ByteArray

/**
 * Platform specific SHA 256 digest.
 *
 * Suspends because the browser's `crypto.subtle.digest` is asynchronous; on other platforms the
 * implementation completes synchronously.
 */
internal expect suspend fun sha256(bytes: ByteArray): ByteArray
