package proj.memorchess.axl.core.data.explorer

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * Live integration test that hits `https://explorer.lichess.ovh` for real.
 *
 * The token comes from the `LICHESS_API_TOKEN` environment variable. CI passes it from the GitHub
 * Actions secret of the same name. Locally, set `LICHESS_API_TOKEN=...` in the JVM run environment;
 * without one the test reports a passing no op so devs can still run the suite.
 *
 * Lichess requires authentication on the explorer endpoints since their February 2026 DDoS
 * mitigation; without a valid token the request returns 401.
 */
class TestLichessExplorerClientIntegration {

  private fun buildClient(token: String): LichessExplorerClient =
    LichessExplorerClient(
      httpClient =
        HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } },
      tokenProvider = { token },
    )

  /** Smoke checks the live Lichess Opening Explorer masters endpoint with a real token. */
  @Test
  fun fetchStartingPositionFromLichessMastersEndpoint() = runTest {
    val token =
      System.getenv("LICHESS_API_TOKEN")?.trim()?.takeIf { it.isNotBlank() }
        ?: run {
          println("Skipping live Lichess test: LICHESS_API_TOKEN not set")
          return@runTest
        }
    val client = buildClient(token)
    val startingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    val result = client.fetch(ExplorerSource.MASTERS, startingFen)

    withClue("Lichess returned non Ok result: $result (token length=${token.length})") {
      result.shouldBeInstanceOf<ExplorerResult.Ok>()
    }
    result.response.moves.shouldNotBeEmpty()
    result.response.opening shouldNotBe null
  }
}
