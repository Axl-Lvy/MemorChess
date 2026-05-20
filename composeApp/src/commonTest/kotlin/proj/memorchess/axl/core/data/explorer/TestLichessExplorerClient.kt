package proj.memorchess.axl.core.data.explorer

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

  private fun buildClient(engine: MockEngine, minGap: kotlin.time.Duration = 0.milliseconds) =
    LichessExplorerClient(
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

    val result = client.fetch(ExplorerSource.MASTERS, fen = "rnb fen")

    result.shouldBeInstanceOf<ExplorerResult.Ok>()
    result.response.opening?.eco shouldBe "B00"
    result.response.moves.first().san shouldBe "e4"
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
        httpClient =
          HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
        maxAttemptsOn429 = 2,
      )

    val result = client.fetch(ExplorerSource.LICHESS, fen = "f")

    result shouldBe ExplorerResult.RateLimited
    // Two attempts because maxAttemptsOn429 = 2 means we make 2 calls total.
    calls shouldBe 2
  }
}
