package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import proj.memorchess.axl.test_util.TestSettings

/** Exercises the full sign in orchestration with a fake launcher and a mocked HTTP client. */
class TestLichessSignInController {

  private val tokenJson = """{"access_token":"tok-xyz"}"""
  private val accountJson = """{"username":"alice"}"""

  private fun successEngine() = MockEngine { request ->
    val response =
      when (request.url.encodedPath) {
        "/api/token" -> tokenJson
        "/api/account" -> accountJson
        else -> error("Unexpected ${request.url}")
      }
    respond(
      content = ByteReadChannel(response),
      status = HttpStatusCode.OK,
      headers = headersOf("Content-Type", "application/json"),
    )
  }

  private fun httpClient(engine: MockEngine): HttpClient =
    HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

  @Test
  fun successPersistsTokenAndUsername() = runTest {
    val tokenStore = OAuthTokenStore(TestSettings())
    val controller =
      LichessSignInController(
        launch = { _, _, state -> OAuthLaunchResult.Ok("code-abc") },
        oauthClient = LichessOAuthClient(httpClient(successEngine())),
        tokenStore = tokenStore,
        redirectUri = "memorchess://oauth",
      )

    val result = controller.signIn()

    result shouldBe SignInResult.Success
    tokenStore.getToken() shouldBe "tok-xyz"
    tokenStore.account.value?.username shouldBe "alice"
  }

  @Test
  fun cancelledLaunchYieldsCancelled() = runTest {
    val tokenStore = OAuthTokenStore(TestSettings())
    val controller =
      LichessSignInController(
        launch = { _, _, _ -> OAuthLaunchResult.Cancelled },
        oauthClient = LichessOAuthClient(httpClient(successEngine())),
        tokenStore = tokenStore,
        redirectUri = "memorchess://oauth",
      )

    val result = controller.signIn()

    result shouldBe SignInResult.Cancelled
    tokenStore.getToken() shouldBe null
  }

  @Test
  fun tokenExchangeFailureSurfacesError() = runTest {
    val tokenStore = OAuthTokenStore(TestSettings())
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.BadRequest) }
    val controller =
      LichessSignInController(
        launch = { _, _, _ -> OAuthLaunchResult.Ok("code-abc") },
        oauthClient = LichessOAuthClient(httpClient(engine)),
        tokenStore = tokenStore,
        redirectUri = "memorchess://oauth",
      )

    val result = controller.signIn()

    result.shouldBeInstanceOf<SignInResult.Failed>()
    tokenStore.getToken() shouldBe null
  }

  @Test
  fun signOutClearsStore() {
    val tokenStore = OAuthTokenStore(TestSettings())
    tokenStore.save("tok-xyz", username = "alice")
    val controller =
      LichessSignInController(
        launch = { _, _, _ -> error("not used") },
        oauthClient = LichessOAuthClient(httpClient(successEngine())),
        tokenStore = tokenStore,
        redirectUri = "memorchess://oauth",
      )

    controller.signOut()

    tokenStore.getToken() shouldBe null
  }
}
