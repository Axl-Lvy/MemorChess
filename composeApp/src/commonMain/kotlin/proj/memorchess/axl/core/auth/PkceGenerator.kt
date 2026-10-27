package proj.memorchess.axl.core.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Generates OAuth 2.0 PKCE (RFC 7636) verifier and `S256` challenge pairs. */
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

/** Generates a random CSRF `state` token. */
@OptIn(ExperimentalEncodingApi::class)
internal fun generateOidcState(): String =
  Base64.UrlSafe.encode(randomBytes(STATE_BYTE_LENGTH)).trimEnd('=')

private const val STATE_BYTE_LENGTH = 32

/** Platform specific cryptographically secure random bytes. */
internal expect fun randomBytes(size: Int): ByteArray

/** SHA-256 digest; suspends because the browser's `crypto.subtle.digest` is asynchronous. */
internal expect suspend fun sha256(bytes: ByteArray): ByteArray
