package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import proj.memorchess.axl.test_util.TestSettings

class TestOidcSignInController {

  private val tokenJson =
    """{"access_token":"tok-xyz","refresh_token":"ref-abc","expires_in":3600}"""

  private fun successEngine() = MockEngine { _ ->
    respond(
      content = ByteReadChannel(tokenJson),
      status = HttpStatusCode.OK,
      headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
  }

  private fun httpClient(engine: MockEngine): HttpClient =
    HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

  private fun controllerWith(
    engine: MockEngine,
    launch: suspend (String, String, String) -> OAuthLaunchResult = { _, _, _ ->
      OAuthLaunchResult.Ok("code-abc")
    },
    tokenStore: OidcTokenStore = OidcTokenStore(TestSettings()),
    now: () -> Instant = { Instant.parse("2026-01-01T00:00:00Z") },
  ) =
    OidcSignInController(
      launch = launch,
      oidcClient = OidcClient(httpClient(engine), issuer = "https://issuer.example"),
      tokenStore = tokenStore,
      redirectUri = "app.memorchess://oauth",
      clientId = "client-1",
      now = now,
    ) to tokenStore

  @Test
  fun successPersistsSession() = runTest {
    val (controller, tokenStore) = controllerWith(successEngine())

    val result = controller.signIn()

    result shouldBe SignInResult.Success
    tokenStore.getAccessToken() shouldBe "tok-xyz"
    tokenStore.getRefreshToken() shouldBe "ref-abc"
  }

  @Test
  fun cancelledLaunchYieldsCancelled() = runTest {
    val (controller, tokenStore) =
      controllerWith(successEngine(), launch = { _, _, _ -> OAuthLaunchResult.Cancelled })

    val result = controller.signIn()

    result shouldBe SignInResult.Cancelled
    tokenStore.getAccessToken() shouldBe null
  }

  @Test
  fun everyLaunchErrorSurfacesFailed() = runTest {
    for (error in OAuthLaunchError.entries) {
      val (controller, tokenStore) =
        controllerWith(successEngine(), launch = { _, _, _ -> OAuthLaunchResult.Error(error) })

      val result = controller.signIn()

      result.shouldBeInstanceOf<SignInResult.Failed>()
      tokenStore.getAccessToken() shouldBe null
    }
  }

  @Test
  fun tokenExchangeRejectionSurfacesFailed() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.BadRequest) }
    val (controller, tokenStore) = controllerWith(engine)

    val result = controller.signIn()

    result.shouldBeInstanceOf<SignInResult.Failed>()
    tokenStore.getAccessToken() shouldBe null
  }

  @Test
  fun signOutClearsStore() {
    val tokenStore = OidcTokenStore(TestSettings())
    tokenStore.save(
      "tok-1",
      "ref-1",
      Instant.parse("2026-01-01T00:00:00Z"),
      Account("user-1", null),
    )
    val (controller, _) = controllerWith(successEngine(), tokenStore = tokenStore)

    controller.signOut()

    tokenStore.getAccessToken() shouldBe null
  }

  @Test
  fun accessTokenWithNoStoredSessionReturnsSignedOut() = runTest {
    val (controller, _) = controllerWith(successEngine())

    controller.accessToken() shouldBe TokenResult.SignedOut
  }

  @Test
  fun accessTokenWithTimeRemainingReturnsStoredTokenWithoutRefreshing() = runTest {
    var refreshCalls = 0
    val engine = MockEngine { _ ->
      refreshCalls++
      respond(content = "", status = HttpStatusCode.InternalServerError)
    }
    val tokenStore = OidcTokenStore(TestSettings())
    val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    tokenStore.save("tok-1", "ref-1", fixedNow + 3600.seconds, Account("user-1", null))
    val (controller, _) = controllerWith(engine, tokenStore = tokenStore, now = { fixedNow })

    val result = controller.accessToken()

    result shouldBe TokenResult.Ok("tok-1")
    refreshCalls shouldBe 0
  }

  @Test
  fun accessTokenWithinBufferOfExpiryRefreshes() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content =
          ByteReadChannel(
            """{"access_token":"tok-refreshed","refresh_token":"ref-new","expires_in":3600}"""
          ),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val tokenStore = OidcTokenStore(TestSettings())
    val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    // 30s left, inside the 60s buffer.
    tokenStore.save("tok-1", "ref-1", fixedNow + 30.seconds, Account("user-1", null))
    val (controller, _) = controllerWith(engine, tokenStore = tokenStore, now = { fixedNow })

    val result = controller.accessToken()

    result shouldBe TokenResult.Ok("tok-refreshed")
    tokenStore.getAccessToken() shouldBe "tok-refreshed"
    tokenStore.getRefreshToken() shouldBe "ref-new"
  }

  @Test
  fun accessTokenExactlyAtBufferBoundaryRefreshes() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content = ByteReadChannel("""{"access_token":"tok-refreshed","expires_in":3600}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val tokenStore = OidcTokenStore(TestSettings())
    val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    // Exactly 60s left: the buffer boundary itself must still refresh (not "more than" 60s).
    tokenStore.save("tok-1", "ref-1", fixedNow + 60.seconds, Account("user-1", null))
    val (controller, _) = controllerWith(engine, tokenStore = tokenStore, now = { fixedNow })

    controller.accessToken() shouldBe TokenResult.Ok("tok-refreshed")
  }

  @Test
  fun accessTokenOneSecondOutsideBufferDoesNotRefresh() = runTest {
    var refreshCalls = 0
    val engine = MockEngine { _ ->
      refreshCalls++
      respond(content = "", status = HttpStatusCode.InternalServerError)
    }
    val tokenStore = OidcTokenStore(TestSettings())
    val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    tokenStore.save("tok-1", "ref-1", fixedNow + 61.seconds, Account("user-1", null))
    val (controller, _) = controllerWith(engine, tokenStore = tokenStore, now = { fixedNow })

    controller.accessToken() shouldBe TokenResult.Ok("tok-1")
    refreshCalls shouldBe 0
  }

  @Test
  fun transientRefreshFailureKeepsSession() = runTest {
    val engine = MockEngine { _ ->
      respond(content = "", status = HttpStatusCode.InternalServerError)
    }
    val tokenStore = OidcTokenStore(TestSettings())
    val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    tokenStore.save("tok-1", "ref-1", fixedNow, Account("user-1", null))
    val (controller, _) = controllerWith(engine, tokenStore = tokenStore, now = { fixedNow })

    val result = controller.accessToken()

    result shouldBe TokenResult.Failed.Transient
    tokenStore.getRefreshToken() shouldBe "ref-1"
    tokenStore.currentAccount.value shouldBe Account("user-1", null)
  }

  @Test
  fun terminalRefreshFailureClearsSession() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.BadRequest) }
    val tokenStore = OidcTokenStore(TestSettings())
    val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    tokenStore.save("tok-1", "ref-1", fixedNow, Account("user-1", null))
    val (controller, _) = controllerWith(engine, tokenStore = tokenStore, now = { fixedNow })

    val result = controller.accessToken()

    result shouldBe TokenResult.Failed.Terminal
    tokenStore.getAccessToken() shouldBe null
    tokenStore.currentAccount.value shouldBe null
  }

  @Test
  fun refreshWithNoRefreshTokenIsTerminal() = runTest {
    var calls = 0
    val engine = MockEngine { _ ->
      calls++
      respond(content = "", status = HttpStatusCode.InternalServerError)
    }
    val tokenStore = OidcTokenStore(TestSettings())
    val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    // A stored access token but no refresh token at all (e.g. the issuer never granted offline
    // access): expired means there is nothing left to try.
    tokenStore.save("tok-1", null, fixedNow, Account("user-1", null))
    val (controller, _) = controllerWith(engine, tokenStore = tokenStore, now = { fixedNow })

    val result = controller.accessToken()

    result shouldBe TokenResult.Failed.Terminal
    calls shouldBe 0
  }
}
