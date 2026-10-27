package proj.memorchess.axl.core.data.explorer

import proj.memorchess.axl.core.engine.Fen
import proj.memorchess.axl.core.auth.LichessToken
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * Live test against `https://explorer.lichess.ovh`, which answers 401 without a token. It reads
 * `LICHESS_API_TOKEN` and passes as a no-op when that variable is unset.
 */
class TestLichessExplorerClientIntegration {

  private fun buildClient(token: String): LichessExplorerClient =
    LichessExplorerClient(
      httpClient =
        HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } },
      tokenProvider = { token?.let(::LichessToken) },
    )

  @Test
  fun fetchStartingPositionFromLichessMastersEndpoint() = runTest {
    val token =
      System.getenv("LICHESS_API_TOKEN")?.trim()?.takeIf { it.isNotBlank() }
        ?: run {
          println("Skipping live Lichess test: LICHESS_API_TOKEN not set")
          return@runTest
        }
    val client = buildClient(token)
    // After 1.e4, since the bare starting position has no opening name to assert on.
    val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"

    val result = client.fetch(ExplorerSource.MASTERS, Fen(afterE4))

    val ok =
      withClue("Lichess returned non Ok result: $result (token length=${token.length})") {
        result.shouldBeInstanceOf<ExplorerResult.Ok>()
      }
    ok.response.moves.shouldNotBeEmpty()
    ok.response.opening?.eco shouldBe "B00"
  }
}
