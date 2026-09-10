package proj.memorchess.axl.server.routes

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.net.URI
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncDeviceRegisterRequest
import proj.memorchess.axl.core.sync.SyncDeviceStatusResponse
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.ServerConfig
import proj.memorchess.axl.server.auth.TEST_AUDIENCE
import proj.memorchess.axl.server.auth.TEST_ISSUER
import proj.memorchess.axl.server.auth.TestJwkProvider
import proj.memorchess.axl.server.auth.TestSigningKey
import proj.memorchess.axl.server.db.PostgresTestDb
import proj.memorchess.axl.server.sync.SyncStore
import proj.memorchess.axl.server.syncModule

class TestDeviceRoutes {

  private val key = TestSigningKey("kid-1")
  private val serverNow = Instant.fromEpochSeconds(1_700_000_000)
  private val store = SyncStore(PostgresTestDb.dataSource())

  private val config =
    ServerConfig(
      port = 0,
      jdbcUrl = "unused",
      dbUser = "unused",
      dbPassword = "unused",
      jwtIssuer = TEST_ISSUER,
      jwtAudience = TEST_AUDIENCE,
      jwksUrl = URI("https://jwks.test/keys"),
      r2Endpoint = URI("https://r2.test/"),
      r2Bucket = "unused",
      r2AccessKeyId = "unused",
      r2SecretAccessKey = "unused",
    )

  /** Runs [block] against the real module, real store and real Postgres, as one fresh caller. */
  private fun withServer(block: suspend (HttpClient, String, String) -> Unit) = testApplication {
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
    block(client, key.token(subject = user), user)
  }

  private suspend fun HttpClient.register(
    token: String,
    deviceId: String = DEVICE,
    platform: DevicePlatform = DevicePlatform.JVM,
    afterReset: Boolean = false,
  ): HttpResponse =
    put("/v1/me/devices/$deviceId") {
      header(HttpHeaders.Authorization, "Bearer $token")
      contentType(ContentType.Application.Json)
      setBody(SYNC_JSON.encodeToString(SyncDeviceRegisterRequest(platform.wireName, afterReset)))
    }

  private suspend fun HttpClient.status(token: String, deviceId: String = DEVICE): HttpResponse =
    get("/v1/me/devices/$deviceId/status") { header(HttpHeaders.Authorization, "Bearer $token") }

  @Test
  fun `a first registration answers 204`() = withServer { client, token, user ->
    client.register(token).status shouldBe HttpStatusCode.NoContent

    store.listDevicesForTest(user).single().platform shouldBe DevicePlatform.JVM
  }

  @Test
  fun `a device id that is not a canonical uuid is refused`() = withServer { client, token, user ->
    val response = client.register(token, deviceId = "not-a-uuid")

    response.status shouldBe HttpStatusCode.BadRequest
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe
      ApiErrorCode.BAD_REQUEST
    store.listDevicesForTest(user).isEmpty() shouldBe true
  }

  @Test
  fun `a platform this build has never heard of is refused`() = withServer { client, token, user ->
    val response =
      client.put("/v1/me/devices/$DEVICE") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody("""{"platform":"fridge","afterReset":false}""")
      }

    response.status shouldBe HttpStatusCode.BadRequest
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe
      ApiErrorCode.BAD_REQUEST
    store.listDevicesForTest(user).isEmpty() shouldBe true
  }

  @Test
  fun `a removed device below the floor is told to resync`() = withServer { client, token, user ->
    client.register(token)
    store.setPositionForTest(user, DEVICE, lastAcked = 99, lastServed = 99)
    store.removeDeviceForTest(user, DEVICE, serverNow)
    store.setGcFloorForTest(user, 100)

    val response = client.register(token)

    response.status shouldBe HttpStatusCode.Gone
    SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe
      ApiErrorCode.RESYNC_REQUIRED
  }

  @Test
  fun `reporting the wipe reinstates the device`() = withServer { client, token, user ->
    client.register(token)
    store.setPositionForTest(user, DEVICE, lastAcked = 99, lastServed = 99)
    store.removeDeviceForTest(user, DEVICE, serverNow)
    store.setGcFloorForTest(user, 100)

    client.register(token, afterReset = true).status shouldBe HttpStatusCode.NoContent

    store.listDevicesForTest(user).single().lastAcked shouldBe 0L
  }

  @Test
  fun `status answers synced for a device with nothing to catch up on`() =
    withServer { client, token, _ ->
      client.register(token)

      val response = client.status(token)

      response.status shouldBe HttpStatusCode.OK
      SYNC_JSON.decodeFromString<SyncDeviceStatusResponse>(response.bodyAsText()).synced shouldBe
        true
    }

  @Test
  fun `status answers not synced for a device that is behind`() =
    withServer { client, token, user ->
      client.register(token)
      store.push(
        user,
        DEVICE,
        SyncPushRequest(
          emptyList(),
          emptyList(),
          listOf(
            SettingSyncRow(
              key = "theme",
              value = "dark",
              isDeleted = false,
              updatedAt = serverNow,
              originDevice = DEVICE,
              deviceSeq = 1,
            )
          ),
        ),
        serverNow,
      )

      SYNC_JSON.decodeFromString<SyncDeviceStatusResponse>(client.status(token).bodyAsText())
        .synced shouldBe false
    }

  @Test
  fun `status answers 404 for a device this caller does not own`() =
    withServer { client, token, _ ->
      client.status(token).status shouldBe HttpStatusCode.NotFound
    }

  @Test
  fun `status refuses a malformed device id`() = withServer { client, token, _ ->
    client.status(token, deviceId = "not-a-uuid").status shouldBe HttpStatusCode.BadRequest
  }

  @Test
  fun `both device routes require a token`() = withServer { client, _, _ ->
    client
      .put("/v1/me/devices/$DEVICE") {
        contentType(ContentType.Application.Json)
        setBody(SYNC_JSON.encodeToString(SyncDeviceRegisterRequest(DevicePlatform.JVM.wireName)))
      }
      .status shouldBe HttpStatusCode.Unauthorized

    client.get("/v1/me/devices/$DEVICE/status").status shouldBe HttpStatusCode.Unauthorized
  }

  private companion object {
    const val DEVICE = "99999999-9999-4999-8999-999999999999"
  }
}
