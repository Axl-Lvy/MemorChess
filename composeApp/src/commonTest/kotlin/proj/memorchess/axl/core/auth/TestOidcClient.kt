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

class TestOidcClient {

  private fun jsonClient(engine: MockEngine): HttpClient =
    HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

  @Test
  fun authorizationUrlContainsRequiredParams() {
    val client =
      OidcClient(
        HttpClient(MockEngine { error("Should not call HTTP") }),
        issuer = "https://issuer.example",
      )
    val url =
      client.buildAuthorizationUrl(
        clientId = "client-1",
        redirectUri = "app.memorchess://oauth",
        codeChallenge = "abc",
        state = "s",
        audience = "https://api.example",
      )
    url shouldContain "https://issuer.example/auth?response_type=code"
    url shouldContain "client_id=client-1"
    url shouldContain "redirect_uri=app.memorchess%3A%2F%2Foauth"
    url shouldContain "code_challenge_method=S256"
    url shouldContain "code_challenge=abc"
    url shouldContain "scope=openid%20offline_access%20profile"
    url shouldContain "resource=https%3A%2F%2Fapi.example"
    url shouldContain "state=s"
  }

  @Test
  fun successfulExchangeReturnsTokenSetAndAccount() = runTest {
    // A minimal unsigned-looking JWT: header.payload.signature, payload =
    // {"sub":"user-1","name":"Alice"}
    val idToken = "eyJhbGciOiJub25lIn0." + "eyJzdWIiOiJ1c2VyLTEiLCJuYW1lIjoiQWxpY2UifQ." + "sig"
    val engine = MockEngine { _ ->
      respond(
        content =
          ByteReadChannel(
            """{"access_token":"tok-xyz","refresh_token":"ref-abc","expires_in":3600,"id_token":"$idToken"}"""
          ),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val client = OidcClient(jsonClient(engine), issuer = "https://issuer.example")

    val result = client.exchangeCode("client-1", "app.memorchess://oauth", "code123", "verifier")

    result.shouldBeInstanceOf<OidcTokenExchangeResult.Ok>()
    result.accessToken shouldBe "tok-xyz"
    result.refreshToken shouldBe "ref-abc"
    result.expiresInSeconds shouldBe 3600L
    result.account shouldBe Account(sub = "user-1", name = "Alice")
  }

  @Test
  fun exchangeWithoutIdTokenReturnsNullAccount() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content = ByteReadChannel("""{"access_token":"tok-xyz","expires_in":3600}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val client = OidcClient(jsonClient(engine), issuer = "https://issuer.example")

    val result = client.exchangeCode("client-1", "app.memorchess://oauth", "code123", "verifier")

    result.shouldBeInstanceOf<OidcTokenExchangeResult.Ok>()
    result.refreshToken shouldBe null
    result.account shouldBe null
  }

  @Test
  fun badRequestReturnsRejected() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.BadRequest) }
    val client = OidcClient(jsonClient(engine), issuer = "https://issuer.example")

    val result = client.exchangeCode("client-1", "app.memorchess://oauth", "bad-code", "verifier")

    result shouldBe OidcTokenExchangeResult.Rejected
  }

  @Test
  fun serverErrorReturnsError() = runTest {
    val engine = MockEngine { _ ->
      respond(content = "", status = HttpStatusCode.InternalServerError)
    }
    val client = OidcClient(jsonClient(engine), issuer = "https://issuer.example")

    val result = client.exchangeCode("client-1", "app.memorchess://oauth", "code123", "verifier")

    result.shouldBeInstanceOf<OidcTokenExchangeResult.Error>()
  }

  @Test
  fun refreshRejectionReturnsRejected() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.BadRequest) }
    val client = OidcClient(jsonClient(engine), issuer = "https://issuer.example")

    val result = client.refresh("client-1", "dead-refresh-token")

    result shouldBe OidcTokenExchangeResult.Rejected
  }

  @Test
  fun successfulRefreshReturnsNewTokenSet() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content =
          ByteReadChannel(
            """{"access_token":"tok-new","refresh_token":"ref-new","expires_in":3600}"""
          ),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val client = OidcClient(jsonClient(engine), issuer = "https://issuer.example")

    val result = client.refresh("client-1", "ref-abc")

    result.shouldBeInstanceOf<OidcTokenExchangeResult.Ok>()
    result.accessToken shouldBe "tok-new"
    result.refreshToken shouldBe "ref-new"
  }
}
