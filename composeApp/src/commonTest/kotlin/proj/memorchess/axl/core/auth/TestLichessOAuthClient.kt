package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class TestLichessOAuthClient {

  private fun jsonClient(engine: MockEngine): HttpClient =
    HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

  @Test
  fun authorizationUrlContainsRequiredParams() {
    val client = LichessOAuthClient(jsonClient(MockEngine { error("Should not call HTTP") }))
    val url =
      client.buildAuthorizationUrl(
        clientId = "memorchess.app",
        redirectUri = "memorchess://oauth",
        codeChallenge = "abc",
        state = "s",
      )
    url shouldContain "response_type=code"
    url shouldContain "client_id=memorchess.app"
    url shouldContain "redirect_uri=memorchess%3A%2F%2Foauth"
    url shouldContain "code_challenge_method=S256"
    url shouldContain "code_challenge=abc"
    url shouldContain "state=s"
  }

  @Test
  fun successfulTokenExchangeReturnsAccessToken() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content = ByteReadChannel("""{"access_token":"tok-xyz","token_type":"Bearer"}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val client = LichessOAuthClient(jsonClient(engine))

    val result =
      client.exchangeCode(
        clientId = "memorchess.app",
        redirectUri = "memorchess://oauth",
        code = "code123",
        codeVerifier = "verifier",
      )

    result.shouldBeInstanceOf<TokenExchangeResult.Ok>()
    result.accessToken shouldBe "tok-xyz"
  }

  @Test
  fun nonSuccessTokenExchangeReturnsError() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.BadRequest) }
    val client = LichessOAuthClient(jsonClient(engine))

    val result = client.exchangeCode("memorchess.app", "memorchess://oauth", "code", "verifier")

    result.shouldBeInstanceOf<TokenExchangeResult.Error>()
  }

  @Test
  fun fetchAccountUnauthorizedReturnsUnauthorized() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.Unauthorized) }
    val client = LichessOAuthClient(jsonClient(engine))

    client.fetchAccount("bad-token") shouldBe AccountResult.Unauthorized
  }

  @Test
  fun fetchAccountReturnsUsername() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content = ByteReadChannel("""{"username":"alice"}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val client = LichessOAuthClient(jsonClient(engine))

    val result = client.fetchAccount("tok-xyz")

    result.shouldBeInstanceOf<AccountResult.Ok>()
    result.username shouldBe "alice"
  }
}
