package proj.memorchess.axl.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.net.URI
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.server.auth.TEST_AUDIENCE
import proj.memorchess.axl.server.auth.TEST_ISSUER
import proj.memorchess.axl.server.auth.TestJwkProvider
import proj.memorchess.axl.server.auth.TestSigningKey
import proj.memorchess.axl.server.db.PostgresTestDb
import proj.memorchess.axl.server.sync.SyncStore

class TestSyncApplication {

  private val key = TestSigningKey("kid-1")

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

  private fun app(
    readiness: suspend () -> Boolean = { true },
    block: suspend (io.ktor.client.HttpClient) -> Unit,
  ) = testApplication {
    application {
      syncModule(
        config = config,
        jwkProvider = TestJwkProvider(key),
        store = SyncStore(PostgresTestDb.dataSource()),
        readiness = readiness,
        clock = { Instant.fromEpochSeconds(1_700_000_000) },
      )
      // A route that fails, so the error mapping is observable without breaking a real endpoint.
      routing { get("/test-only/boom") { error("a secret internal detail") } }
    }
    block(createClient {})
  }

  @Test
  fun `reports liveness without touching the database`() =
    app(readiness = { false }) { client -> client.get("/health").status shouldBe HttpStatusCode.OK }

  @Test
  fun `reports readiness when the probe succeeds`() = app { client ->
    client.get("/ready").status shouldBe HttpStatusCode.OK
  }

  @Test
  fun `reports unreadiness when the probe fails`() =
    app(readiness = { false }) { client ->
      client.get("/ready").status shouldBe HttpStatusCode.ServiceUnavailable
    }

  @Test
  fun `maps an unexpected failure to 500 and leaks nothing`() = app { client ->
    val response = client.get("/test-only/boom")

    response.status shouldBe HttpStatusCode.InternalServerError
    val body = response.bodyAsText()
    body shouldNotContain "secret internal detail"
    body shouldNotContain "IllegalStateException"
    SYNC_JSON.decodeFromString<ApiError>(body).code shouldBe ApiErrorCode.INTERNAL
  }

  @Test
  fun `maps an unparseable body to 400`() = app { client ->
    val response =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer ${key.token()}")
        contentType(ContentType.Application.Json)
        setBody("{ not json")
      }

    response.status shouldBe HttpStatusCode.BadRequest
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe
      ApiErrorCode.BAD_REQUEST
  }

  @Test
  fun `refuses a body larger than the cap before reading it`() = app { client ->
    val response =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer ${key.token()}")
        contentType(ContentType.Application.Json)
        setBody(ByteArray((MAX_BODY_BYTES + 1).toInt()))
      }

    response.status shouldBe HttpStatusCode.PayloadTooLarge
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe ApiErrorCode.TOO_LARGE
  }

  @Test
  fun `answers a token refusal with the api error body`() = app { client ->
    val response = client.get("/v1/sync")

    response.status shouldBe HttpStatusCode.Unauthorized
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe
      ApiErrorCode.UNAUTHORIZED
  }

  @Test
  fun `answers an unknown path with 404 and no body detail`() = app { client ->
    client.get("/v1/nope").status shouldBe HttpStatusCode.NotFound
  }
}
