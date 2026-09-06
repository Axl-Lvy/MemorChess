package proj.memorchess.axl.core.sync

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
import kotlinx.coroutines.test.runTest

class TestSyncApiClient {

  private fun client(engine: MockEngine) =
    SyncApiClient(
      HttpClient(engine) { install(ContentNegotiation) { json(SYNC_JSON) } },
      baseUrl = "https://issuer.example/v1",
    )

  @Test
  fun successfulPullReturnsResponse() = runTest {
    val engine = MockEngine { request ->
      request.url.encodedPath shouldBe "/v1/sync"
      respond(
        content =
          ByteReadChannel(
            """{"serverTime":"2026-01-01T00:00:00Z","nextCursor":null,"nodes":[],"edges":[],"settings":[]}"""
          ),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

    val result = client(engine).pull("tok", since = null, limit = 500)

    result.shouldBeInstanceOf<SyncPullOutcome.Ok>()
    result.response.nextCursor shouldBe null
  }

  @Test
  fun unauthorizedPullReturnsUnauthorized() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.Unauthorized) }

    client(engine).pull("tok", since = null, limit = 500) shouldBe SyncPullOutcome.Unauthorized
  }

  @Test
  fun serverErrorPullReturnsError() = runTest {
    val engine = MockEngine { _ ->
      respond(content = "", status = HttpStatusCode.InternalServerError)
    }

    client(engine)
      .pull("tok", since = null, limit = 500)
      .shouldBeInstanceOf<SyncPullOutcome.Error>()
  }

  @Test
  fun rateLimitedPullReturnsRateLimited() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.TooManyRequests) }

    client(engine).pull("tok", since = null, limit = 500) shouldBe SyncPullOutcome.RateLimited
  }

  @Test
  fun successfulPushReturnsResponse() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content =
          ByteReadChannel("""{"serverTime":"2026-01-01T00:00:00Z","revision":5,"rejected":[]}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

    val result =
      client(engine)
        .push(
          "tok",
          SyncPushRequest(nodes = emptyList(), edges = emptyList(), settings = emptyList()),
        )

    result.shouldBeInstanceOf<SyncPushOutcome.Ok>()
    result.response.revision shouldBe 5L
  }

  @Test
  fun tooLargePushReturnsTooLarge() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.PayloadTooLarge) }

    client(engine).push("tok", SyncPushRequest(emptyList(), emptyList(), emptyList())) shouldBe
      SyncPushOutcome.TooLarge
  }

  @Test
  fun quotaExceededPushReturnsQuotaExceeded() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content = ByteReadChannel("""{"code":"quota_exceeded","message":"too many nodes"}"""),
        status = HttpStatusCode.Forbidden,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

    client(engine).push("tok", SyncPushRequest(emptyList(), emptyList(), emptyList())) shouldBe
      SyncPushOutcome.QuotaExceeded
  }

  @Test
  fun forbiddenPushWithoutTheQuotaCodeReturnsError() = runTest {
    // 403 is not unambiguous the way 401/413 are: branch on ApiError.code, never on status alone.
    val engine = MockEngine { _ ->
      respond(
        content = ByteReadChannel("""{"code":"forbidden","message":"not your repertoire"}"""),
        status = HttpStatusCode.Forbidden,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

    client(engine)
      .push("tok", SyncPushRequest(emptyList(), emptyList(), emptyList()))
      .shouldBeInstanceOf<SyncPushOutcome.Error>()
  }

  @Test
  fun rateLimitedPushReturnsRateLimited() = runTest {
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.TooManyRequests) }

    client(engine).push("tok", SyncPushRequest(emptyList(), emptyList(), emptyList())) shouldBe
      SyncPushOutcome.RateLimited
  }
}
