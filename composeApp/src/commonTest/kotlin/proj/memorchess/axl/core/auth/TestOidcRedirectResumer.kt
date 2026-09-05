package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
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
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import proj.memorchess.axl.test_util.TestSettings

class TestOidcRedirectResumer {

  // --- decideOidcRedirectExchange: pure, no I/O ---

  @Test
  fun pathNotMatchingRedirectPathIsNotACallback() {
    val decision =
      decideOidcRedirectExchange(
        currentPath = "/",
        redirectPath = "/sync-oauth-callback",
        queryParams = emptyMap(),
        pending = null,
      )

    decision shouldBe OidcRedirectDecision.NotACallback
  }

  @Test
  fun matchingStateWithPendingRecordYieldsExchange() {
    val pending = PendingOidcRedirect(state = "s1", codeVerifier = "v1", returnHash = "#settings")

    val decision =
      decideOidcRedirectExchange(
        currentPath = "/sync-oauth-callback",
        redirectPath = "/sync-oauth-callback",
        queryParams = mapOf("code" to "abc", "state" to "s1"),
        pending = pending,
      )

    decision shouldBe
      OidcRedirectDecision.Callback(
        cleanedUrl = "/#settings",
        exchange = PendingExchange(code = "abc", codeVerifier = "v1"),
      )
  }

  @Test
  fun stateMismatchYieldsNoExchangeButStillCleansUrl() {
    val pending =
      PendingOidcRedirect(state = "expected", codeVerifier = "v1", returnHash = "#settings")

    val decision =
      decideOidcRedirectExchange(
        currentPath = "/sync-oauth-callback",
        redirectPath = "/sync-oauth-callback",
        queryParams = mapOf("code" to "abc", "state" to "different"),
        pending = pending,
      )

    decision shouldBe OidcRedirectDecision.Callback(cleanedUrl = "/#settings", exchange = null)
  }

  @Test
  fun noPendingRecordYieldsNoExchange() {
    val decision =
      decideOidcRedirectExchange(
        currentPath = "/sync-oauth-callback",
        redirectPath = "/sync-oauth-callback",
        queryParams = mapOf("code" to "abc", "state" to "s1"),
        pending = null,
      )

    decision shouldBe OidcRedirectDecision.Callback(cleanedUrl = "/", exchange = null)
  }

  @Test
  fun idpErrorResponseYieldsNoExchangeEvenWithMatchingState() {
    val pending = PendingOidcRedirect(state = "s1", codeVerifier = "v1", returnHash = "#settings")

    val decision =
      decideOidcRedirectExchange(
        currentPath = "/sync-oauth-callback",
        redirectPath = "/sync-oauth-callback",
        queryParams = mapOf("error" to "access_denied", "state" to "s1"),
        pending = pending,
      )

    decision shouldBe OidcRedirectDecision.Callback(cleanedUrl = "/#settings", exchange = null)
  }

  @Test
  fun missingCodeYieldsNoExchange() {
    val pending = PendingOidcRedirect(state = "s1", codeVerifier = "v1", returnHash = "#settings")

    val decision =
      decideOidcRedirectExchange(
        currentPath = "/sync-oauth-callback",
        redirectPath = "/sync-oauth-callback",
        queryParams = mapOf("state" to "s1"),
        pending = pending,
      )

    decision shouldBe OidcRedirectDecision.Callback(cleanedUrl = "/#settings", exchange = null)
  }

  // --- exchangeOidcRedirectCode: async, needs a real OidcClient/OidcTokenStore ---

  private fun httpClient(engine: MockEngine): HttpClient =
    HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

  @Test
  fun successfulExchangePersistsTokensAndCallsOnSignedIn() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content =
          ByteReadChannel(
            """{"access_token":"tok-xyz","refresh_token":"ref-abc","expires_in":3600}"""
          ),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val tokenStore = OidcTokenStore(TestSettings())
    var signedInCalls = 0

    exchangeOidcRedirectCode(
      oidcClient = OidcClient(httpClient(engine), issuer = "https://issuer.example"),
      tokenStore = tokenStore,
      clientId = "client-1",
      redirectUri = "https://app.example/sync-oauth-callback",
      exchange = PendingExchange(code = "abc", codeVerifier = "v1"),
      now = { Instant.parse("2026-01-01T00:00:00Z") },
      onSignedIn = { signedInCalls++ },
    )

    tokenStore.getAccessToken() shouldBe "tok-xyz"
    signedInCalls shouldBe 1
  }

  @Test
  fun rejectedExchangeDoesNotCallOnSignedIn() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.BadRequest) }
    val tokenStore = OidcTokenStore(TestSettings())
    var signedInCalls = 0

    exchangeOidcRedirectCode(
      oidcClient = OidcClient(httpClient(engine), issuer = "https://issuer.example"),
      tokenStore = tokenStore,
      clientId = "client-1",
      redirectUri = "https://app.example/sync-oauth-callback",
      exchange = PendingExchange(code = "abc", codeVerifier = "v1"),
      onSignedIn = { signedInCalls++ },
    )

    tokenStore.getAccessToken() shouldBe null
    signedInCalls shouldBe 0
  }

  @Test
  fun errorExchangeDoesNotCallOnSignedIn() = runTest {
    val engine = MockEngine { _ ->
      respond(content = "", status = HttpStatusCode.InternalServerError)
    }
    val tokenStore = OidcTokenStore(TestSettings())
    var signedInCalls = 0

    exchangeOidcRedirectCode(
      oidcClient = OidcClient(httpClient(engine), issuer = "https://issuer.example"),
      tokenStore = tokenStore,
      clientId = "client-1",
      redirectUri = "https://app.example/sync-oauth-callback",
      exchange = PendingExchange(code = "abc", codeVerifier = "v1"),
      onSignedIn = { signedInCalls++ },
    )

    signedInCalls shouldBe 0
  }
}
