package proj.memorchess.axl.core.data.explorer

import proj.memorchess.axl.core.engine.Fen
import proj.memorchess.axl.core.auth.LichessToken
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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class TestLichessExplorerClient {

  private val sampleJson =
    """
    {
      "white": 10,
      "draws": 5,
      "black": 3,
      "moves": [{"uci":"e2e4","san":"e4","white":10,"draws":5,"black":3}],
      "opening": {"eco":"B00","name":"King's Pawn"}
    }
    """
      .trimIndent()

  private fun buildClient(
    engine: MockEngine,
    minGap: kotlin.time.Duration = 0.milliseconds,
    userAgentProvider: () -> String? = { null },
  ) =
    LichessExplorerClient(
      tokenProvider = { LichessToken("test-token") },
      userAgentProvider = userAgentProvider,
      httpClient =
        HttpClient(engine) {
          install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
      minGap = minGap,
    )

  @Test
  fun successfulFetchReturnsParsedResponse() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content = ByteReadChannel(sampleJson),
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", "application/json"),
      )
    }
    val client = buildClient(engine)

    val result = client.fetch(ExplorerSource.MASTERS, fen = Fen("rnb fen"))

    result.shouldBeInstanceOf<ExplorerResult.Ok>()
    result.response.opening?.eco shouldBe "B00"
    result.response.moves.first().san shouldBe "e4"
  }

  @Test
  fun serverErrorReturnsNetworkError() = runTest {
    val engine = MockEngine { _ ->
      respond(content = "", status = HttpStatusCode.InternalServerError)
    }
    val client = buildClient(engine)

    val result = client.fetch(ExplorerSource.LICHESS, fen = Fen("f"))

    result.shouldBeInstanceOf<ExplorerResult.NetworkError>()
  }

  @Test
  fun sendsUserAgentHeaderWhenProviderReturnsOne() = runTest {
    var sentUserAgent: String? = null
    val engine = MockEngine { request ->
      sentUserAgent = request.headers["User-Agent"]
      respond(
        content = ByteReadChannel(sampleJson),
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", "application/json"),
      )
    }
    val client = buildClient(engine, userAgentProvider = { "test-agent" })

    client.fetch(ExplorerSource.MASTERS, fen = Fen("rnb fen"))

    sentUserAgent shouldBe "test-agent"
  }

  @Test
  fun omitsUserAgentHeaderWhenProviderReturnsNull() = runTest {
    var sentUserAgent: String? = "unset"
    val engine = MockEngine { request ->
      sentUserAgent = request.headers["User-Agent"]
      respond(
        content = ByteReadChannel(sampleJson),
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", "application/json"),
      )
    }
    // wasmJs sends none: a script supplied User-Agent fails the explorer's CORS preflight on
    // Firefox.
    val client = buildClient(engine, userAgentProvider = { null })

    client.fetch(ExplorerSource.MASTERS, fen = Fen("rnb fen"))

    sentUserAgent shouldBe null
  }

  @Test
  fun rateLimitedAfterRetriesReturnsRateLimited() = runTest {
    var calls = 0
    val engine = MockEngine { _ ->
      calls++
      respond(content = "", status = HttpStatusCode.TooManyRequests)
    }
    val client =
      LichessExplorerClient(
        tokenProvider = { LichessToken("test-token") },
        httpClient =
          HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
        maxAttemptsOn429 = 2,
      )

    val result = client.fetch(ExplorerSource.LICHESS, fen = Fen("f"))

    result shouldBe ExplorerResult.RateLimited
    calls shouldBe 2
  }
}
