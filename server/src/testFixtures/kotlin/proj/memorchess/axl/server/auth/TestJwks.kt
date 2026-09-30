package proj.memorchess.axl.server.auth

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** RSA key size. 2048 is the floor every OIDC issuer meets and is fast enough to generate. */
private const val KEY_BITS = 2048

/**
 * A real RSA signing key with a real JWK published for it.
 *
 * @property keyId The `kid` this key is published under.
 */
internal class TestSigningKey(internal val keyId: String) {

  private val pair =
    KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()

  private val algorithm =
    Algorithm.RSA256(pair.public as RSAPublicKey, pair.private as RSAPrivateKey)

  /** The JWK an issuer would publish for this key. */
  internal fun jwk(): Jwk =
    Jwk.fromValues(
      mapOf(
        "kty" to "RSA",
        "kid" to keyId,
        "alg" to "RS256",
        "use" to "sig",
        "n" to (pair.public as RSAPublicKey).modulus.toUrlSafeBase64(),
        "e" to (pair.public as RSAPublicKey).publicExponent.toUrlSafeBase64(),
      )
    )

  /** Signs a token, defaulting every claim to something the server should accept. */
  internal fun token(
    subject: String = "auth0|caller",
    issuer: String = TEST_ISSUER,
    audience: String = TEST_AUDIENCE,
    expiresIn: Duration = 1.hours,
    keyId: String = this.keyId,
  ): String =
    JWT.create()
      .withKeyId(keyId)
      .withIssuer(issuer)
      .withAudience(audience)
      .apply { if (subject.isNotEmpty()) withSubject(subject) }
      .withExpiresAt(
        java.util.Date.from(
          java.time.Instant.ofEpochMilli((Clock.System.now() + expiresIn).toEpochMilliseconds())
        )
      )
      .sign(algorithm)
}

private fun java.math.BigInteger.toUrlSafeBase64(): String =
  Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray().dropLeadingZero())

// BigInteger.toByteArray() prepends a zero byte when the high bit is set, to keep the value
// positive. A JWK modulus is unsigned, so that byte must go or the key does not match.
private fun ByteArray.dropLeadingZero(): ByteArray =
  if (size > 1 && this[0] == 0.toByte()) copyOfRange(1, size) else this

/** The issuer every route test configures. */
internal const val TEST_ISSUER: String = "https://issuer.test/"

/** The audience every route test configures. */
internal const val TEST_AUDIENCE: String = "memorchess-test"

/** A provider serving a fixed set of keys, as an issuer's JWKS endpoint would. */
internal class TestJwkProvider(private vararg val keys: TestSigningKey) : JwkProvider {

  override fun get(keyId: String?): Jwk =
    keys.firstOrNull { it.keyId == keyId }?.jwk()
      ?: throw SigningKeyNotFoundException("no key for kid $keyId", null)
}
