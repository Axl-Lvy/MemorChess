package proj.memorchess.axl.server

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.net.URI
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.server.auth.TEST_AUDIENCE
import proj.memorchess.axl.server.auth.TEST_ISSUER
import proj.memorchess.axl.server.auth.TestJwkProvider
import proj.memorchess.axl.server.auth.TestSigningKey
import proj.memorchess.axl.server.db.PostgresTestDb
import proj.memorchess.axl.server.repertoire.InMemoryRepertoireBlobStore
import proj.memorchess.axl.server.repertoire.RepertoireStore
import proj.memorchess.axl.server.routes.repertoireModule
import proj.memorchess.axl.server.sync.SyncStore

/** Verifies the per key request budgets [installRateLimiting] enforces on every tier. */
class TestRateLimiting {

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
      r2Endpoint = URI("https://r2.test/"),
      r2Bucket = "unused",
      r2AccessKeyId = "unused",
      r2SecretAccessKey = "unused",
    )

  private val tiers =
    RateLimitTiers(
      syncWrite = RateLimitTier(limit = 2, refillPeriod = 1.minutes),
      syncRead = RateLimitTier(limit = 3, refillPeriod = 1.minutes),
      publicRead = RateLimitTier(limit = 2, refillPeriod = 1.minutes),
      admin = RateLimitTier(limit = 2, refillPeriod = 1.minutes),
    )

  private fun withServer(block: suspend (HttpClient) -> Unit) = testApplication {
    application {
      syncModule(
        config = config,
        jwkProvider = TestJwkProvider(key),
        store = SyncStore(PostgresTestDb.dataSource()),
        readiness = { true },
        clock = { Instant.fromEpochSeconds(1_700_000_000) },
        rateLimits = tiers,
      )
      repertoireModule(
        store = RepertoireStore(PostgresTestDb.dataSource(), InMemoryRepertoireBlobStore()),
        rateLimits = tiers,
      )
    }
    block(createClient { install(ContentNegotiation) { json(SYNC_JSON) } })
  }

  @Test
  fun `refuses a caller past its write budget with a rate limited api error`() =
    withServer { client ->
      val token = key.token(subject = PostgresTestDb.newUserId())
      repeat(tiers.syncWrite.limit) { client.deleteMe(token) }

      val response = client.deleteMe(token)

      response.status shouldBe HttpStatusCode.TooManyRequests
      SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe
        ApiErrorCode.RATE_LIMITED
    }

  @Test
  fun `gives each caller its own write budget`() = withServer { client ->
    val exhausted = key.token(subject = PostgresTestDb.newUserId())
    val fresh = key.token(subject = PostgresTestDb.newUserId())
    repeat(tiers.syncWrite.limit + 1) { client.deleteMe(exhausted) }

    client.deleteMe(fresh).status shouldBe HttpStatusCode.NoContent
  }

  @Test
  fun `keeps the read budget independent from the write budget`() = withServer { client ->
    val token = key.token(subject = PostgresTestDb.newUserId())
    repeat(tiers.syncWrite.limit + 1) { client.deleteMe(token) }

    client.get("/v1/sync") { header(HttpHeaders.Authorization, "Bearer $token") }.status shouldBe
      HttpStatusCode.OK
  }

  @Test
  fun `refuses an anonymous caller past its public read budget`() = withServer { client ->
    repeat(tiers.publicRead.limit) { client.manifest("1.2.3.4") }

    client.manifest("1.2.3.4").status shouldBe HttpStatusCode.TooManyRequests
  }

  @Test
  fun `gives each ip its own public read budget`() = withServer { client ->
    repeat(tiers.publicRead.limit + 1) { client.manifest("1.2.3.4") }

    client.manifest("5.6.7.8").status shouldBe HttpStatusCode.OK
  }

  @Test
  fun `rate limits the admin route by ip`() = withServer { client ->
    repeat(tiers.admin.limit) {
      client.post("/admin/repertoires/some-id/status") { header("CF-Connecting-IP", "9.9.9.9") }
    }

    client
      .post("/admin/repertoires/some-id/status") { header("CF-Connecting-IP", "9.9.9.9") }
      .status shouldBe HttpStatusCode.TooManyRequests
  }

  private suspend fun HttpClient.deleteMe(token: String) =
    delete("/v1/me") { header(HttpHeaders.Authorization, "Bearer $token") }

  private suspend fun HttpClient.manifest(ip: String) =
    get("/v1/repertoires/manifest.json") { header("CF-Connecting-IP", ip) }
}
