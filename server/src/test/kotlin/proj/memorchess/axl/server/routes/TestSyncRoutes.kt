package proj.memorchess.axl.server.routes

import proj.memorchess.axl.server.auth.UserId
import proj.memorchess.axl.core.sync.DeviceId
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
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncEraseRequest
import proj.memorchess.axl.core.sync.SyncEraseResponse
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

  /** Primes device rows for callers the harness did not create. */
  private val registrar = SyncStore(PostgresTestDb.dataSource())

  /** A token for a fresh caller whose device is already registered. */
  private suspend fun otherCaller(): String {
    val subject = PostgresTestDb.newUserId()
    registrar.registerDevice(
      subject,
      DeviceId(DEVICE),
      DevicePlatform.JVM,
      afterReset = false,
      serverNow,
    )
    return key.token(subject = subject.value)
  }

  /** Runs [block] against the real module, real store and real Postgres, as one caller. */
  private fun withServer(
    store: SyncStore = SyncStore(PostgresTestDb.dataSource()),
    devices: List<String> = listOf(DEVICE),
    block: suspend (HttpClient, String) -> Unit,
  ) = testApplication {
    val user = PostgresTestDb.newUserId()
    application {
      syncModule(
        config = config,
        jwkProvider = TestJwkProvider(key),
        store = store,
        readiness = { true },
        clock = { serverNow },
      )
    }
    val client = createClient { install(ContentNegotiation) { json(SYNC_JSON) } }
    // Registration gates pulling, so the device rows are primed directly.
    for (device in devices) {
      store.registerDevice(user, DeviceId(device), DevicePlatform.JVM, afterReset = false, serverNow)
    }
    block(client, key.token(subject = user.value))
  }

  private fun setting(key: String, value: String, at: Instant = serverNow) =
    SettingSyncRow(
      key = key,
      value = value,
      isDeleted = false,
      updatedAt = at,
      originDevice = DeviceId(DEVICE),
      deviceSeq = 1,
    )

  private suspend fun HttpClient.push(token: String, vararg rows: SettingSyncRow) =
    post("/v1/sync") {
      header(HttpHeaders.Authorization, "Bearer $token")
      contentType(ContentType.Application.Json)
      setBody(
        SYNC_JSON.encodeToString(
          SyncPushRequest(emptyList(), emptyList(), rows.toList(), device = DeviceId(DEVICE))
        )
      )
    }

  private suspend fun HttpClient.pull(token: String, query: String = "") =
    get("/v1/sync?device=$DEVICE&$query") { header(HttpHeaders.Authorization, "Bearer $token") }

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
  fun `returns nothing once the page is acknowledged`() = withServer { client, token ->
    client.push(token, setting("a", "1"))
    val first = SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(token).bodyAsText())

    val page =
      SYNC_JSON.decodeFromString<SyncPullResponse>(
        client.pull(token, "ack=${first.pageToken}").bodyAsText()
      )

    page.settings shouldHaveSize 0
    page.nextCursor shouldBe null
  }

  @Test
  fun `pages when more rows exist than the requested limit`() = withServer { client, token ->
    for (index in 1..5) client.push(token, setting("key-$index", "$index"))

    val first =
      SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(token, "limit=2").bodyAsText())

    first.settings shouldHaveSize 2

    val second =
      SYNC_JSON.decodeFromString<SyncPullResponse>(
        client.pull(token, "ack=${first.pageToken}&limit=2").bodyAsText()
      )

    second.settings shouldHaveSize 2
  }

  @Test
  fun `clamps a limit above the cap instead of refusing it`() = withServer { client, token ->
    client.push(token, setting("a", "1"))

    val response = client.pull(token, "limit=${MAX_PULL_LIMIT * 10}")

    response.status shouldBe HttpStatusCode.OK
    SYNC_JSON.decodeFromString<SyncPullResponse>(response.bodyAsText()).settings shouldHaveSize 1
  }

  @Test
  fun `refuses a push whose body omits the device entirely`() = withServer { client, token ->
    val response =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody("""{"nodes":[],"edges":[],"settings":[]}""")
      }

    response.status shouldBe HttpStatusCode.BadRequest
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
  }

  @Test
  fun `refuses a push that names an empty device`() = withServer { client, token ->
    val response =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody("""{"nodes":[],"edges":[],"settings":[],"device":""}""")
      }

    response.status shouldBe HttpStatusCode.BadRequest
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
  }

  @Test
  fun `refuses a pull that names no device`() = withServer { client, token ->
    val response = client.get("/v1/sync") { header(HttpHeaders.Authorization, "Bearer $token") }

    response.status shouldBe HttpStatusCode.BadRequest
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
  }

  @Test
  fun `refuses a pull from a device that is not a canonical UUID`() =
    withServer(devices = listOf(DEVICE, "not-a-uuid")) { client, token ->
      val response =
        client.get("/v1/sync?device=not-a-uuid") {
          header(HttpHeaders.Authorization, "Bearer $token")
        }

      response.status shouldBe HttpStatusCode.BadRequest
      SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
    }

  @Test
  fun `refuses a pull from the nil UUID reserved for erase tombstones`() =
    withServer(devices = listOf(DEVICE, NIL_UUID)) { client, token ->
      val response =
        client.get("/v1/sync?device=$NIL_UUID") {
          header(HttpHeaders.Authorization, "Bearer $token")
        }

      response.status shouldBe HttpStatusCode.BadRequest
      SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
    }

  @Test
  fun `an unacknowledged token confirms nothing and re serves the page`() =
    withServer { client, token ->
      client.push(token, setting("a", "1"))
      SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(token).bodyAsText())

      val retry =
        SYNC_JSON.decodeFromString<SyncPullResponse>(
          client.pull(token, "ack=not-a-token").bodyAsText()
        )

      retry.settings shouldHaveSize 1
    }

  @Test
  fun `refuses a limit that is not a positive number`() = withServer { client, token ->
    client.pull(token, "limit=0").status shouldBe HttpStatusCode.BadRequest
    client.pull(token, "limit=-5").status shouldBe HttpStatusCode.BadRequest
    client.pull(token, "limit=lots").status shouldBe HttpStatusCode.BadRequest
  }

  @Test
  fun `refuses a batch with more rows than the cap`() = withServer { client, token ->
    val rows = (1..MAX_PUSH_ROWS + 1).map { setting("key-$it", "$it") }

    val response =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(
          SYNC_JSON.encodeToString(SyncPushRequest(emptyList(), emptyList(), rows, device = DeviceId(DEVICE)))
        )
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
          originDevice = DeviceId("device-a"),
          deviceSeq = 1,
        )
      }

    val response =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(SYNC_JSON.encodeToString(SyncPushRequest(nodes, edges, settings, device = DeviceId(DEVICE))))
      }

    response.status shouldBe HttpStatusCode.PayloadTooLarge
  }

  @Test
  fun `never lets one caller see another's rows`() = withServer { client, token ->
    client.push(token, setting("mine", "1"))
    val other = otherCaller()

    val page = SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(other).bodyAsText())

    page.settings shouldHaveSize 0
  }

  @Test
  fun `deletes every row the caller owns`() = withServer { client, token ->
    client.push(token, setting("theme", "dark"))

    val deleted = client.delete("/v1/me") { header(HttpHeaders.Authorization, "Bearer $token") }

    deleted.status shouldBe HttpStatusCode.NoContent
    // Deletion clears the device rows too, so the caller must register again.
    client.pull(token).status shouldBe HttpStatusCode.BadRequest
  }

  @Test
  fun `leaves other callers untouched when one account is deleted`() = withServer { client, token ->
    val survivor = otherCaller()
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

  @Test
  fun `refuses a push that would cross the caller's storage quota`() =
    withServer(store = SyncStore(PostgresTestDb.dataSource(), maxNodesPerUser = 0)) { client, token
      ->
      val response =
        client.post("/v1/sync") {
          header(HttpHeaders.Authorization, "Bearer $token")
          contentType(ContentType.Application.Json)
          setBody(
            SYNC_JSON.encodeToString(
              SyncPushRequest(
                nodes =
                  listOf(
                    NodeSyncRow(
                      positionKey = "fen-1",
                      dueDate = serverNow,
                      lastReview = null,
                      firstReview = null,
                      stability = 1.5,
                      difficulty = 5.0,
                      reps = 0,
                      lapses = 0,
                      phase = "REVIEW",
                      step = 0,
                      isDeleted = false,
                      updatedAt = serverNow,
                      originDevice = DeviceId("device-a"),
                      deviceSeq = 1,
                    )
                  ),
                edges = emptyList(),
                settings = emptyList(),
                device = DeviceId(DEVICE),
              )
            )
          )
        }

      response.status shouldBe HttpStatusCode.Forbidden
      SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "quota_exceeded"
    }

  private fun node(positionKey: String) =
    NodeSyncRow(
      positionKey = positionKey,
      dueDate = serverNow,
      lastReview = null,
      firstReview = null,
      stability = 1.5,
      difficulty = 5.0,
      reps = 0,
      lapses = 0,
      phase = "REVIEW",
      step = 0,
      isDeleted = false,
      updatedAt = serverNow,
      originDevice = DeviceId(DEVICE),
      deviceSeq = 1,
    )

  private suspend fun HttpClient.erase(token: String?, body: String) =
    post("/v1/me/erase") {
      if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
      contentType(ContentType.Application.Json)
      setBody(body)
    }

  private fun eraseBody(device: String, deviceSeq: Long = 1) =
    SYNC_JSON.encodeToString(SyncEraseRequest(DeviceId(device), deviceSeq))

  @Test
  fun `erase requires authentication`() = withServer { client, _ ->
    client.erase(null, eraseBody(DEVICE)).status shouldBe HttpStatusCode.Unauthorized
  }

  @Test
  fun `erase refuses an empty device`() = withServer { client, token ->
    val response = client.erase(token, eraseBody(""))

    response.status shouldBe HttpStatusCode.BadRequest
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
  }

  @Test
  fun `erase refuses a device that is not a canonical UUID`() = withServer { client, token ->
    val response = client.erase(token, eraseBody("not-a-uuid"))

    response.status shouldBe HttpStatusCode.BadRequest
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
  }

  @Test
  fun `erase refuses a deviceSeq below one`() = withServer { client, token ->
    for (deviceSeq in listOf(0L, -1L)) {
      val response = client.erase(token, eraseBody(DEVICE, deviceSeq))

      response.status shouldBe HttpStatusCode.BadRequest
      SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
    }
  }

  @Test
  fun `erase refuses the nil UUID reserved for erase tombstones`() =
    withServer(devices = listOf(DEVICE, NIL_UUID)) { client, token ->
      val response = client.erase(token, eraseBody(NIL_UUID))

      response.status shouldBe HttpStatusCode.BadRequest
      SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe "bad_request"
    }

  @Test
  fun `erase returns the highest revision and tombstones what the caller held`() =
    withServer { client, token ->
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(
          SYNC_JSON.encodeToString(
            SyncPushRequest(listOf(node("fen-1")), emptyList(), emptyList(), device = DeviceId(DEVICE))
          )
        )
      }

      val response = client.erase(token, eraseBody(DEVICE))

      response.status shouldBe HttpStatusCode.OK
      val erased = SYNC_JSON.decodeFromString<SyncEraseResponse>(response.bodyAsText())
      erased.serverTime shouldBe serverNow
      (erased.revision > 0) shouldBe true
      val page = SYNC_JSON.decodeFromString<SyncPullResponse>(client.pull(token).bodyAsText())
      page.nodes shouldHaveSize 1
      page.nodes.single().positionKey shouldBe "fen-1"
      page.nodes.single().isDeleted shouldBe true
    }

  @Test
  fun `erase from an unknown device is mapped like a push`() = withServer { client, token ->
    val stranger = "66666666-6666-4666-8666-666666666666"
    val pushed =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(
          SYNC_JSON.encodeToString(
            SyncPushRequest(emptyList(), emptyList(), emptyList(), device = DeviceId(stranger))
          )
        )
      }

    val erased = client.erase(token, eraseBody(stranger))

    erased.status shouldBe pushed.status
    erased.status shouldBe HttpStatusCode.BadRequest
  }

  private companion object {
    const val DEVICE = "55555555-5555-4555-8555-555555555555"
    const val NIL_UUID = "00000000-0000-0000-0000-000000000000"
  }
}
