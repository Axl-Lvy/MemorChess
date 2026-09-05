package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import proj.memorchess.axl.test_util.TestSettings

class TestOidcRedirectSignInController {

  private fun httpClient(): HttpClient =
    HttpClient(MockEngine { respond(content = "", status = HttpStatusCode.InternalServerError) }) {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

  @Test
  fun signInPersistsPendingRecordAndNavigatesButNeverReturns() = runTest {
    val pendingStore = PendingOidcRedirectStore(TestSettings())
    // A CompletableDeferred awaited off the virtual test dispatcher, not advanceUntilIdle(),
    // because PkceGenerator.generate() suspends on a genuinely async platform primitive (the
    // browser's crypto.subtle.digest on wasmJs): its completion is never scheduled on the test
    // scheduler's virtual queue, so neither advancing virtual time nor a virtual-time
    // withTimeout ever observes it — only real time does.
    val navigatedTo = CompletableDeferred<String>()
    val delegate =
      OidcSignInController(
        launch = { _, _, _ -> error("popup launch must never be called on wasmJs") },
        oidcClient = OidcClient(httpClient(), issuer = "https://issuer.example"),
        tokenStore = OidcTokenStore(TestSettings()),
        redirectUri = "https://app.example/sync-oauth-callback",
        clientId = "client-1",
      )
    val controller =
      OidcRedirectSignInController(
        delegate = delegate,
        oidcClient = OidcClient(httpClient(), issuer = "https://issuer.example"),
        pendingStore = pendingStore,
        redirectUri = "https://app.example/sync-oauth-callback",
        clientId = "client-1",
        audience = "https://api.example",
        navigate = { navigatedTo.complete(it) },
        currentHash = { "#settings" },
      )

    val job = launch { controller.signIn() }
    val url =
      withContext(Dispatchers.Default.limitedParallelism(1)) {
        withTimeout(5.seconds) { navigatedTo.await() }
      }

    url shouldContain "https://issuer.example/auth"
    val pending = pendingStore.load()
    pending shouldNotBe null
    pending!!.returnHash shouldBe "#settings"
    job.cancel()
  }

  @Test
  fun signOutDelegatesToWrappedController() {
    val tokenStore = OidcTokenStore(TestSettings())
    tokenStore.save(
      "tok-1",
      "ref-1",
      Instant.parse("2026-01-01T00:00:00Z"),
      Account("u1", null),
    )
    val delegate =
      OidcSignInController(
        launch = { _, _, _ -> error("must not be called") },
        oidcClient = OidcClient(httpClient(), issuer = "https://issuer.example"),
        tokenStore = tokenStore,
        redirectUri = "https://app.example/sync-oauth-callback",
        clientId = "client-1",
      )
    val controller =
      OidcRedirectSignInController(
        delegate = delegate,
        oidcClient = OidcClient(httpClient(), issuer = "https://issuer.example"),
        pendingStore = PendingOidcRedirectStore(TestSettings()),
        redirectUri = "https://app.example/sync-oauth-callback",
        clientId = "client-1",
        audience = "https://api.example",
        navigate = {},
        currentHash = { "" },
      )

    controller.signOut()

    tokenStore.getAccessToken() shouldBe null
  }
}
