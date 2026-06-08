package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import proj.memorchess.axl.test_util.TestSettings

/**
 * JVM only coverage for the convenience constructor that binds to the platform [OAuthLauncher].
 *
 * The common tests all use the primary constructor with a `launch` lambda, so the launcher-backed
 * overload and its delegating lambda are only reachable here, where a real [OAuthLauncher] exists.
 */
class TestLichessSignInControllerJvm {

  private fun httpClient(): HttpClient =
    HttpClient(MockEngine { _ -> respond(content = "", status = HttpStatusCode.BadRequest) }) {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

  @Test
  fun convenienceConstructorDelegatesToPlatformLauncher() = runTest {
    val tokenStore = OAuthTokenStore(TestSettings())
    // An out-of-range port makes the loopback server fail to bind, so launch() returns a
    // PLATFORM_ERROR immediately without opening a browser.
    val controller =
      LichessSignInController(
        launcher = OAuthLauncher(),
        oauthClient = LichessOAuthClient(httpClient()),
        tokenStore = tokenStore,
        redirectUri = "http://127.0.0.1:99999/callback",
      )

    val result = controller.signIn()

    result.shouldBeInstanceOf<SignInResult.Failed>()
    tokenStore.getToken() shouldBe null
  }
}
