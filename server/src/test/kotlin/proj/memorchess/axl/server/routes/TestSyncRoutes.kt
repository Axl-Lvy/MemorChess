package proj.memorchess.axl.server.routes

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.net.URI
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPullResponse
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncPushResponse
import proj.memorchess.axl.server.ServerConfig
import proj.memorchess.axl.server.auth.TEST_AUDIENCE
import proj.memorchess.axl.server.auth.TEST_ISSUER
import proj.memorchess.axl.server.auth.TestJwkProvider
import proj.memorchess.axl.server.auth.TestSigningKey
import proj.memorchess.axl.server.db.PostgresTestDb
import proj.memorchess.axl.server.sync.SyncStore
import proj.memorchess.axl.server.syncModule

class TestSyncRoutes {

  private val key = TestSigningKey("kid-1")
  private val serverNow = Instant.fromEpochSeconds(1_700_000_000)

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

  /** Runs [block] against the real module, real store and real Postgres, as one caller. */
  private fun withServer(block: suspend (HttpClient, String) -> Unit) = testApplication {
    val user = PostgresTestDb.newUserId()
    application {
      syncModule(
        config = config,
        jwkProvider = TestJwkProvider(key),
        store = SyncStore(PostgresTestDb.dataSource()),
        readiness = { true },
        clock = { serverNow },
      )
    }
    val client = createClient { install(ContentNegotiation) { json(SYNC_JSON) } }
    block(client, key.token(subject = user))
  }

  private fun setting(key: String, value: String, at: Instant = serverNow) =
    SettingSyncRow(
      key = key,
      value = value,
      isDeleted = false,
      updatedAt = at,
      originDevice = "device-a",
      deviceSeq = 1,
    )

  private suspend fun HttpClient.push(token: String, vararg rows: SettingSyncRow) =
    post("/v1/sync") {
      header(HttpHeaders.Authorization, "Bearer $token")
      contentType(ContentType.Application.Json)
      setBody(SYNC_JSON.encodeToString(SyncPushRequest(emptyList(), emptyList(), rows.toList())))
    }

  private suspend fun HttpClient.pull(token: String, query: String = "") =
    get("/v1/sync$query") { header(HttpHeaders.Authorization, "Bearer $token") }

  @Test
  fun `pushes a row and reads it back`() = withServer { client, token ->
    val pushed = client.push(token, setting("theme", "dark"))
    pushed.status shouldBe HttpStatusCode.OK

    val page = SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(token).bodyAsText())

    page.settings shouldHaveSize 1
    page.settings.single().key shouldBe "theme"
    page.settings.single().value shouldBe "dark"
  }

  @Test
  fun `stores the row byte identically to the one that was sent`() = withServer { client, token ->
    val sent = setting("theme", "dark", at = serverNow - 3.minutes)
    client.push(token, sent)

    val page = SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(token).bodyAsText())

    page.settings.single() shouldBe sent
  }

  @Test
  fun `reports the server clock and the revision on a push`() = withServer { client, token ->
    val body = client.push(token, setting("theme", "dark")).bodyAsText()

    val response = SYNC_JSON.decodeFromString<SyncPushResponse>(body)

    response.serverTime shouldBe serverNow
    (response.revision > 0) shouldBe true
    response.rejected shouldHaveSize 0
  }

  @Test
  fun `refuses a row stamped too far in the future`() = withServer { client, token ->
    val body =
      client.push(token, setting("theme", "dark", at = serverNow + 10.minutes)).bodyAsText()

    val response = SYNC_JSON.decodeFromString<SyncPushResponse>(body)

    response.rejected shouldHaveSize 1
    response.rejected.single().code shouldBe RejectionCode.CLOCK_TOO_FAR_AHEAD
    response.rejected.single().id shouldBe "theme"
  }

  @Test
  fun `starts from the beginning when since is absent`() = withServer { client, token ->
    client.push(token, setting("a", "1"))

    val page = SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(token).bodyAsText())

    page.settings shouldHaveSize 1
  }

  @Test
  fun `returns nothing above the caller's own cursor`() = withServer { client, token ->
    val revision =
      SYNC_JSON.decodeFromString<SyncPushResponse>(
          client.push(token, setting("a", "1")).bodyAsText()
        )
        .revision

    val page =
      SYNC_JSON.decodeFromString<SyncPullResponse>(
        client.pull(token, "?since=$revision").bodyAsText()
      )

    page.settings shouldHaveSize 0
    page.nextCursor shouldBe null
  }

  @Test
  fun `pages when more rows exist than the requested limit`() = withServer { client, token ->
    for (index in 1..5) client.push(token, setting("key-$index", "$index"))

    val first =
      SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(token, "?limit=2").bodyAsText())

    first.settings shouldHaveSize 2
    first.nextCursor shouldBe first.nextCursor!!

    val second =
      SYNC_JSON.decodeFromString<SyncPullResponse>(
        client.pull(token, "?since=${first.nextCursor}&limit=2").bodyAsText()
      )

    second.settings shouldHaveSize 2
  }

  @Test
  fun `clamps a limit above the cap instead of refusing it`() = withServer { client, token ->
    client.push(token, setting("a", "1"))

    val response = client.pull(token, "?limit=${MAX_PULL_LIMIT * 10}")

    response.status shouldBe HttpStatusCode.OK
    SYNC_JSON.decodeFromString<SyncPullResponse>(response.bodyAsText()).settings shouldHaveSize 1
  }

  @Test
  fun `refuses a since that is not a number`() = withServer { client, token ->
    val response = client.pull(token, "?since=yesterday")

    response.status shouldBe HttpStatusCode.BadRequest
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
  }

  @Test
  fun `refuses a negative since`() = withServer { client, token ->
    client.pull(token, "?since=-1").status shouldBe HttpStatusCode.BadRequest
  }

  @Test
  fun `refuses a limit that is not a positive number`() = withServer { client, token ->
    client.pull(token, "?limit=0").status shouldBe HttpStatusCode.BadRequest
    client.pull(token, "?limit=-5").status shouldBe HttpStatusCode.BadRequest
    client.pull(token, "?limit=lots").status shouldBe HttpStatusCode.BadRequest
  }

  @Test
  fun `refuses a batch with more rows than the cap`() = withServer { client, token ->
    val rows = (1..MAX_PUSH_ROWS + 1).map { setting("key-$it", "$it") }

    val response =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(SYNC_JSON.encodeToString(SyncPushRequest(emptyList(), emptyList(), rows)))
      }

    response.status shouldBe HttpStatusCode.PayloadTooLarge
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "too_large"
  }

  @Test
  fun `counts every resource against the batch cap`() = withServer { client, token ->
    // Split just over the cap across two resources, so a per resource count would let it through.
    val half = MAX_PUSH_ROWS / 2 + 1
    val settings = (1..half).map { setting("key-$it", "$it") }
    val nodes = emptyList<NodeSyncRow>()
    val edges =
      (1..half).map {
        EdgeSyncRow(
          origin = "origin-$it",
          destination = "destination-$it",
          move = "e4",
          isGood = true,
          isDeleted = false,
          updatedAt = serverNow,
          originDevice = "device-a",
          deviceSeq = 1,
        )
      }

    val response =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(SYNC_JSON.encodeToString(SyncPushRequest(nodes, edges, settings)))
      }

    response.status shouldBe HttpStatusCode.PayloadTooLarge
  }

  @Test
  fun `never lets one caller see another's rows`() = withServer { client, token ->
    client.push(token, setting("mine", "1"))
    val other = key.token(subject = PostgresTestDb.newUserId())

    val page = SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(other).bodyAsText())

    page.settings shouldHaveSize 0
  }

  @Test
  fun `deletes every row the caller owns`() = withServer { client, token ->
    client.push(token, setting("theme", "dark"))

    val deleted = client.delete("/v1/me") { header(HttpHeaders.Authorization, "Bearer $token") }

    deleted.status shouldBe HttpStatusCode.NoContent
    SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(token).bodyAsText())
      .settings shouldHaveSize 0
  }

  @Test
  fun `leaves other callers untouched when one account is deleted`() = withServer { client, token ->
    val survivor = key.token(subject = PostgresTestDb.newUserId())
    client.push(token, setting("mine", "1"))
    client.push(survivor, setting("theirs", "2"))

    client.delete("/v1/me") { header(HttpHeaders.Authorization, "Bearer $token") }

    SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(survivor).bodyAsText())
      .settings shouldHaveSize 1
  }

  @Test
  fun `requires a token on every endpoint`() = withServer { client, _ ->
    client.get("/v1/sync").status shouldBe HttpStatusCode.Unauthorized
    client.post("/v1/sync") { setBody("{}") }.status shouldBe HttpStatusCode.Unauthorized
    client.delete("/v1/me").status shouldBe HttpStatusCode.Unauthorized
  }
}
