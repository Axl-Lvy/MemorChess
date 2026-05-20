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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import proj.memorchess.axl.test_util.InMemoryExplorerCache

class TestCachedExplorer {

  private val sample =
    LichessExplorerResponse(
      white = 1,
      draws = 1,
      black = 1,
      moves =
        listOf(LichessExplorerMove(uci = "e2e4", san = "e4", white = 1, draws = 1, black = 1)),
      opening = LichessOpening(eco = "B00", name = "King's Pawn"),
    )

  private val sampleJson =
    """
    {"white":1,"draws":1,"black":1,
     "moves":[{"uci":"e2e4","san":"e4","white":1,"draws":1,"black":1}],
     "opening":{"eco":"B00","name":"King's Pawn"}}
    """
      .trimIndent()

  @Test
  fun freshCacheHitDoesNotCallNetworkForMasters() = runTest {
    val cache = InMemoryExplorerCache().also { it.seed("fen1", ExplorerSource.MASTERS, sample) }
    var networkCalls = 0
    val client =
      LichessExplorerClient(
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ ->
              networkCalls++
              respond(
                content = ByteReadChannel(sampleJson),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
              )
            }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val explorer = CachedExplorer(client, cache)

    val result = explorer.fetch(ExplorerSource.MASTERS, "fen1")

    result.shouldBeInstanceOf<CachedExplorerResult.Fresh>()
    networkCalls shouldBe 0
  }

  @Test
  fun lichessCacheExpiresAfterTtl() = runTest {
    val cache =
      InMemoryExplorerCache().also {
        it.seed(
          "fen2",
          ExplorerSource.LICHESS,
          sample,
          fetchedAt = Instant.parse("2020-01-01T00:00:00Z"),
        )
      }
    var networkCalls = 0
    val client =
      LichessExplorerClient(
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ ->
              networkCalls++
              respond(
                content = ByteReadChannel(sampleJson),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
              )
            }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val explorer = CachedExplorer(client, cache, lichessTtl = 7.days)

    val result = explorer.fetch(ExplorerSource.LICHESS, "fen2")

    result.shouldBeInstanceOf<CachedExplorerResult.Fresh>()
    networkCalls shouldBe 1
  }

  @Test
  fun networkErrorWithStaleCacheReturnsStale() = runTest {
    val cache =
      InMemoryExplorerCache().also {
        it.seed(
          "fen3",
          ExplorerSource.LICHESS,
          sample,
          fetchedAt = Instant.parse("2020-01-01T00:00:00Z"),
        )
      }
    val client =
      LichessExplorerClient(
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ -> respond(content = "", status = HttpStatusCode.InternalServerError) }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val explorer = CachedExplorer(client, cache, lichessTtl = 7.days)

    val result = explorer.fetch(ExplorerSource.LICHESS, "fen3")

    result.shouldBeInstanceOf<CachedExplorerResult.Stale>()
  }

  @Test
  fun networkErrorWithoutCacheReturnsNetworkError() = runTest {
    val cache = InMemoryExplorerCache()
    val client =
      LichessExplorerClient(
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ -> respond(content = "", status = HttpStatusCode.InternalServerError) }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val explorer = CachedExplorer(client, cache)

    val result = explorer.fetch(ExplorerSource.MASTERS, "fenX")

    result.shouldBeInstanceOf<CachedExplorerResult.NetworkError>()
  }

  @Test
  fun unauthorizedWithoutCacheReturnsUnauthorized() = runTest {
    val cache = InMemoryExplorerCache()
    val client =
      LichessExplorerClient(
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ -> respond(content = "", status = HttpStatusCode.Unauthorized) }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val explorer = CachedExplorer(client, cache)

    val result = explorer.fetch(ExplorerSource.LICHESS, "fenNoAuth")

    result shouldBe CachedExplorerResult.Unauthorized
  }

  @Test
  fun unauthorizedWithStaleCacheReturnsStale() = runTest {
    val cache =
      InMemoryExplorerCache().also {
        it.seed(
          "fenAuthStale",
          ExplorerSource.LICHESS,
          sample,
          fetchedAt = Instant.parse("2020-01-01T00:00:00Z"),
        )
      }
    val client =
      LichessExplorerClient(
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ -> respond(content = "", status = HttpStatusCode.Unauthorized) }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val explorer = CachedExplorer(client, cache, lichessTtl = 7.days)

    val result = explorer.fetch(ExplorerSource.LICHESS, "fenAuthStale")

    result.shouldBeInstanceOf<CachedExplorerResult.Stale>()
  }

  @Test
  fun rateLimitedWithoutCacheReturnsRateLimited() = runTest {
    val cache = InMemoryExplorerCache()
    val client =
      LichessExplorerClient(
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ -> respond(content = "", status = HttpStatusCode.TooManyRequests) }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
        maxAttemptsOn429 = 1,
      )
    val explorer = CachedExplorer(client, cache)

    val result = explorer.fetch(ExplorerSource.LICHESS, "fenY")

    result shouldBe CachedExplorerResult.RateLimited
  }

  @Test
  fun rateLimitedWithStaleCacheReturnsStale() = runTest {
    val cache =
      InMemoryExplorerCache().also {
        it.seed(
          "fenZ",
          ExplorerSource.LICHESS,
          sample,
          fetchedAt = Instant.parse("2020-01-01T00:00:00Z"),
        )
      }
    val client =
      LichessExplorerClient(
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ -> respond(content = "", status = HttpStatusCode.TooManyRequests) }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
        maxAttemptsOn429 = 1,
      )
    val explorer = CachedExplorer(client, cache, lichessTtl = 7.days)

    val result = explorer.fetch(ExplorerSource.LICHESS, "fenZ")

    result.shouldBeInstanceOf<CachedExplorerResult.Stale>()
  }

  @Test
  fun lichessCacheWithinTtlSkipsNetwork() = runTest {
    val recent = proj.memorchess.axl.core.date.DateUtil.now()
    val cache =
      InMemoryExplorerCache().also { it.seed("fenFresh", ExplorerSource.LICHESS, sample, recent) }
    var networkCalls = 0
    val client =
      LichessExplorerClient(
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ ->
              networkCalls++
              respond(content = "", status = HttpStatusCode.InternalServerError)
            }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val explorer = CachedExplorer(client, cache, lichessTtl = 7.days)

    val result = explorer.fetch(ExplorerSource.LICHESS, "fenFresh")

    result.shouldBeInstanceOf<CachedExplorerResult.Fresh>()
    networkCalls shouldBe 0
  }
}
