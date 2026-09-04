package proj.memorchess.axl.server.auth

import com.auth0.jwk.SigningKeyNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.URI
import java.security.interfaces.RSAPublicKey
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.sync.SYNC_JSON

/** The shape an issuer publishes at its JWKS endpoint. */
@Serializable private data class Jwks(val keys: List<JwkJson>)

@Serializable
private data class JwkJson(
  val kty: String,
  val kid: String,
  val alg: String,
  val use: String,
  val n: String,
  val e: String,
)

class TestJwksProvider {

  private fun TestSigningKey.asJson(): JwkJson {
    val values = jwk().additionalAttributes
    return JwkJson(
      kty = "RSA",
      kid = keyId,
      alg = "RS256",
      use = "sig",
      n = values.getValue("n") as String,
      e = values.getValue("e") as String,
    )
  }

  /** Serves a JWKS on an ephemeral port and hands the resolved URL to [block]. */
  private fun servingJwks(vararg keys: TestSigningKey, block: (URI) -> Unit) {
    val body = Jwks(keys.map { it.asJson() })
    val server =
      embeddedServer(Netty, port = 0) {
          install(ContentNegotiation) { json(SYNC_JSON) }
          routing { get("/.well-known/jwks.json") { call.respond(body) } }
        }
        .start(wait = false)
    try {
      val port = runBlocking { server.engine.resolvedConnectors().first().port }
      block(URI("http://127.0.0.1:$port/.well-known/jwks.json"))
    } finally {
      server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
    }
  }

  @Test
  fun `resolves a published key by kid`() {
    val key = TestSigningKey("kid-live")

    servingJwks(key) { url ->
      val resolved = jwksProvider(url).get("kid-live")

      resolved.id shouldBe "kid-live"
      (resolved.publicKey as RSAPublicKey).algorithm shouldBe "RSA"
    }
  }

  @Test
  fun `fails for a kid the issuer does not publish`() {
    val key = TestSigningKey("kid-live")

    servingJwks(key) { url ->
      shouldThrow<SigningKeyNotFoundException> { jwksProvider(url).get("kid-absent") }
    }
  }

  @Test
  fun `resolves each of several published keys, as during a rotation`() {
    val outgoing = TestSigningKey("kid-old")
    val incoming = TestSigningKey("kid-new")

    servingJwks(outgoing, incoming) { url ->
      val provider = jwksProvider(url)

      provider.get("kid-old").id shouldBe "kid-old"
      provider.get("kid-new").id shouldBe "kid-new"
    }
  }
}
