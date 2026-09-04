package proj.memorchess.axl.server.auth

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.net.URI
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.server.ServerConfig

class TestJwtAuth {

  private val key = TestSigningKey("kid-1")

  /** A second keypair published under the same kid, so only the signature can tell them apart. */
  private val impostor = TestSigningKey("kid-1")

  private val config =
    ServerConfig(
      port = 0,
      jdbcUrl = "unused",
      dbUser = "unused",
      dbPassword = "unused",
      jwtIssuer = TEST_ISSUER,
      jwtAudience = TEST_AUDIENCE,
      jwksUrl = URI("https://issuer.test/jwks.json"),
    )

  /** Mounts one authenticated route echoing the caller id, which is the whole contract. */
  private fun Application.echoCaller(provider: com.auth0.jwk.JwkProvider) {
    installJwtAuth(config, provider)
    routing { authenticate(SYNC_AUTH) { get("/whoami") { call.respondText(call.callerId) } } }
  }

  private fun withAuth(
    provider: com.auth0.jwk.JwkProvider = TestJwkProvider(key),
    block: suspend (io.ktor.client.HttpClient) -> Unit,
  ) = testApplication {
    // The challenge answers with an ApiError, which needs a serializer installed. Task 5 installs
    // this in the production module; here it is the test's own scaffolding.
    application {
      install(ContentNegotiation) { json(SYNC_JSON) }
      echoCaller(provider)
    }
    block(createClient {})
  }

  @Test
  fun `accepts a well formed token and exposes sub as the caller`() = withAuth { client ->
    val response =
      client.get("/whoami") {
        header(HttpHeaders.Authorization, "Bearer ${key.token(subject = "google|42")}")
      }

    response.status shouldBe HttpStatusCode.OK
    response.bodyAsText() shouldBe "google|42"
  }

  @Test
  fun `refuses a request with no authorization header`() = withAuth { client ->
    val response = client.get("/whoami")

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `answers a refusal with the api error body and no detail`() = withAuth { client ->
    val response = client.get("/whoami")

    val body = SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText())
    body.code shouldBe ApiErrorCode.UNAUTHORIZED
  }

  @Test
  fun `refuses a token signed by an unpublished key`() = withAuth { client ->
    val response = client.get("/whoami") { header(HttpHeaders.Authorization, "Bearer ${impostor.token()}") }

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `refuses a token whose kid is unknown to the issuer`() = withAuth { client ->
    val response =
      client.get("/whoami") {
        header(HttpHeaders.Authorization, "Bearer ${key.token(keyId = "kid-rotated-away")}")
      }

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `refuses a token from another issuer`() = withAuth { client ->
    val response =
      client.get("/whoami") {
        header(HttpHeaders.Authorization, "Bearer ${key.token(issuer = "https://evil.test/")}")
      }

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `refuses a token minted for another audience`() = withAuth { client ->
    val response =
      client.get("/whoami") {
        header(HttpHeaders.Authorization, "Bearer ${key.token(audience = "some-other-app")}")
      }

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `refuses an expired token`() = withAuth { client ->
    val response =
      client.get("/whoami") {
        header(HttpHeaders.Authorization, "Bearer ${key.token(expiresIn = -1.hours)}")
      }

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `refuses a token with no subject`() = withAuth { client ->
    val response =
      client.get("/whoami") { header(HttpHeaders.Authorization, "Bearer ${key.token(subject = "")}") }

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `refuses a subject longer than the cap`() = withAuth { client ->
    val response =
      client.get("/whoami") {
        header(HttpHeaders.Authorization, "Bearer ${key.token(subject = "s".repeat(256))}")
      }

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `refuses a garbage authorization header`() = withAuth { client ->
    val response = client.get("/whoami") { header(HttpHeaders.Authorization, "Bearer not-a-token") }

    response.status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `refuses a token presented without the bearer scheme`() = withAuth { client ->
    val response = client.get("/whoami") { header(HttpHeaders.Authorization, key.token()) }

    response.status shouldBe HttpStatusCode.Unauthorized
  }
}
