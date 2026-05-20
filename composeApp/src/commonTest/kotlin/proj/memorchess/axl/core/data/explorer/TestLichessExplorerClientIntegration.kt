package proj.memorchess.axl.core.data.explorer

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Ignore
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * Live integration test that hits `https://explorer.lichess.ovh` for real.
 *
 * Disabled in CI by default because:
 * 1. CI runners have no guaranteed outbound network to lichess.org.
 * 2. We do not want to hammer Lichess from every build.
 *
 * **To run locally**, remove the [Ignore] annotation on the test method, then `./gradlew
 * :composeApp:jvmTest --tests
 * proj.memorchess.axl.core.data.explorer.TestLichessExplorerClientIntegration`.
 *
 * The test fetches the master games response for the starting position and asserts that the opening
 * metadata and at least one move row come back.
 */
class TestLichessExplorerClientIntegration {

  private fun buildClient(): LichessExplorerClient =
    LichessExplorerClient(
      httpClient =
        HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    )

  /**
   * Smoke checks the live Lichess Opening Explorer masters endpoint.
   *
   * Run manually after taking the [Ignore] off the method. See class KDoc for the gradle
   * invocation.
   */
  @Ignore
  @Test
  fun fetchStartingPositionFromLichessMastersEndpoint() = runTest {
    val client = buildClient()
    val startingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    val result = client.fetch(ExplorerSource.MASTERS, startingFen)

    result.shouldBeInstanceOf<ExplorerResult.Ok>()
    result.response.moves.shouldNotBeEmpty()
    result.response.opening shouldNotBe null
  }
}
