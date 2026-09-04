package proj.memorchess.axl.server.routes

import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.net.URI
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import proj.memorchess.axl.core.sync.SYNC_JSON
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
import proj.memorchess.axl.server.sync.SyncTransport
import proj.memorchess.axl.server.sync.TestDevice
import proj.memorchess.axl.server.syncModule

/** Drives the real routes with a real token, so the wire format is part of the property. */
private class HttpTransport(private val client: HttpClient, private val token: String) :
  SyncTransport {

  override suspend fun push(request: SyncPushRequest, serverNow: Instant): SyncPushResponse {
    val response =
      client.post("/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(SYNC_JSON.encodeToString(request))
      }
    return SYNC_JSON.decodeFromString(response.bodyAsText())
  }

  override suspend fun pull(since: Long, limit: Int, serverNow: Instant): SyncPullResponse {
    val response =
      client.get("/v1/sync?since=$since&limit=$limit") {
        header(HttpHeaders.Authorization, "Bearer $token")
      }
    return SYNC_JSON.decodeFromString(response.bodyAsText())
  }
}

class TestHttpConvergence {

  private val key = TestSigningKey("kid-1")
  private val base = Instant.fromEpochSeconds(1_700_000_000)

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

  /**
   * Runs [block] with two devices on one account, both reaching the server over HTTP, and a server
   * clock the test moves.
   */
  private fun twoDevices(
    block: suspend (TestDevice, SyncTransport, TestDevice, SyncTransport, ServerClock) -> Unit
  ) = testApplication {
    val user = PostgresTestDb.newUserId()
    val clock = ServerClock(base)
    application {
      syncModule(
        config = config,
        jwkProvider = TestJwkProvider(key),
        store = SyncStore(PostgresTestDb.dataSource()),
        readiness = { true },
        clock = clock::now,
      )
    }
    val client = createClient { install(ContentNegotiation) { json(SYNC_JSON) } }
    val token = key.token(subject = user)
    block(
      TestDevice("device-a"),
      HttpTransport(client, token),
      TestDevice("device-b"),
      HttpTransport(client, token),
      clock,
    )
  }

  /** Server time the test controls, so the skew boundary is reachable through HTTP. */
  internal class ServerClock(private var current: Instant) {
    internal fun now(): Instant = current

    internal fun set(at: Instant) {
      current = at
    }
  }

  @Test
  fun `a row written on one device reaches the other`() = twoDevices { a, toA, b, toB, clock ->
    a.edit("theme", "dark", clock.now())

    a.sync(toA, clock.now())
    b.sync(toB, clock.now())

    b.visible() shouldBe mapOf("theme" to "dark")
  }

  @Test
  fun `the later write wins on both devices`() = twoDevices { a, toA, b, toB, clock ->
    a.edit("theme", "dark", clock.now())
    a.sync(toA, clock.now())
    b.sync(toB, clock.now())

    clock.set(base + 1.minutes)
    b.edit("theme", "light", clock.now())
    b.sync(toB, clock.now())
    a.sync(toA, clock.now())

    a.visible() shouldBe mapOf("theme" to "light")
    b.visible() shouldBe mapOf("theme" to "light")
  }

  @Test
  fun `a deletion propagates as a tombstone`() = twoDevices { a, toA, b, toB, clock ->
    a.edit("theme", "dark", clock.now())
    a.sync(toA, clock.now())
    b.sync(toB, clock.now())

    clock.set(base + 1.minutes)
    a.delete("theme", clock.now())
    a.sync(toA, clock.now())
    b.sync(toB, clock.now())

    b.visible() shouldHaveSize 0
    b.snapshot().getValue("theme").isDeleted shouldBe true
  }

  @Test
  fun `a row stamped beyond the tolerance is refused, re-stamped and lands`() =
    twoDevices { a, toA, b, toB, clock ->
      a.edit("theme", "dark", clock.now() + 10.minutes)

      // First sync is refused for skew, and the device re-stamps against server time.
      a.sync(toA, clock.now())
      a.sync(toA, clock.now())
      b.sync(toB, clock.now())

      b.visible() shouldBe mapOf("theme" to "dark")
    }

  @Test
  fun `converges under interleaved edits over HTTP`() = twoDevices { a, toA, b, toB, clock ->
    val random = Random(seed = 7)
    val pairs = listOf(a to toA, b to toB)

    repeat(60) { step ->
      clock.set(base + (step * 30).seconds)
      val (device, transport) = pairs[random.nextInt(pairs.size)]
      when (random.nextInt(3)) {
        0 -> device.edit("key-${random.nextInt(4)}", "v$step", clock.now())
        1 -> device.delete("key-${random.nextInt(4)}", clock.now())
        else -> device.sync(transport, clock.now())
      }
    }

    // Drain: each device pushes and pulls until neither has anything left to send.
    repeat(3) {
      a.sync(toA, clock.now())
      b.sync(toB, clock.now())
    }

    a.snapshot() shouldBe b.snapshot()
  }
}
