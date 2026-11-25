package proj.memorchess.axl.core.data.explorer

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.auth.LichessToken
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.Fen
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.test_util.InMemoryExplorerCache

@OptIn(ExperimentalCoroutinesApi::class)
class TestExplorerViewModel {

  private val sampleJson =
    """
    {"white":1,"draws":0,"black":0,"moves":[],"opening":null}
    """
      .trimIndent()

  private fun buildClient() =
    LichessExplorerClient(
      tokenProvider = { LichessToken("test-token") },
      httpClient =
        HttpClient(
          MockEngine { _ ->
            respond(
              content = ByteReadChannel(sampleJson),
              status = HttpStatusCode.OK,
              headers = headersOf("Content-Type", "application/json"),
            )
          },
        ) {
          install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
      minGap = 0.milliseconds,
    )

  @Test
  fun setFenTriggersLoadingThenLoaded() = runTest {
    val cached = CachedExplorer(buildClient(), InMemoryExplorerCache())
    val vm = ExplorerViewModel(cached, scope = backgroundScope, debounce = 0.milliseconds)

    vm.setFen(Fen("test fen"))
    // Wait until the view model has progressed past Idle. Cannot rely on
    // testScheduler.advanceUntilIdle here because Ktor's MockEngine dispatches on a real thread.
    val loaded = vm.state.first { it is ExplorerState.Loaded } as ExplorerState.Loaded

    loaded.source shouldBe ExplorerSource.MASTERS
  }

  @Test
  fun setSourceUpdatesStateFlow() = runTest {
    val cached = CachedExplorer(buildClient(), InMemoryExplorerCache())
    val vm = ExplorerViewModel(cached, scope = backgroundScope, debounce = 0.milliseconds)

    vm.setSource(ExplorerSource.LICHESS)

    vm.source.value shouldBe ExplorerSource.LICHESS
  }

  @Test
  fun sourceSwitchTriggersRefetch() = runTest {
    val cached = CachedExplorer(buildClient(), InMemoryExplorerCache())
    val vm = ExplorerViewModel(cached, scope = backgroundScope, debounce = 0.milliseconds)

    vm.setFen(Fen("fen for switch"))
    val firstLoaded = vm.state.first { it is ExplorerState.Loaded } as ExplorerState.Loaded
    firstLoaded.source shouldBe ExplorerSource.MASTERS

    vm.setSource(ExplorerSource.LICHESS)
    val secondLoaded =
      vm.state.first { it is ExplorerState.Loaded && it.source == ExplorerSource.LICHESS }
        as ExplorerState.Loaded
    secondLoaded.source shouldBe ExplorerSource.LICHESS
  }

  @Test
  fun networkFailureLeadsToErrorState() = runTest {
    val errClient =
      LichessExplorerClient(
        tokenProvider = { LichessToken("test-token") },
        httpClient =
          HttpClient(
            MockEngine { _ -> respond(content = "", status = HttpStatusCode.InternalServerError) },
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val cached = CachedExplorer(errClient, InMemoryExplorerCache())
    val vm = ExplorerViewModel(cached, scope = backgroundScope, debounce = 0.milliseconds)

    vm.setFen(Fen("fen with error"))
    val err = vm.state.first { it is ExplorerState.Error } as ExplorerState.Error
    err.source shouldBe ExplorerSource.MASTERS
  }

  @Test
  fun unauthorizedLeadsToUnauthorizedState() = runTest {
    val unauthClient =
      LichessExplorerClient(
        tokenProvider = { LichessToken("test-token") },
        httpClient =
          HttpClient(
            MockEngine { _ -> respond(content = "", status = HttpStatusCode.Unauthorized) },
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val cached = CachedExplorer(unauthClient, InMemoryExplorerCache())
    val vm = ExplorerViewModel(cached, scope = backgroundScope, debounce = 0.milliseconds)

    vm.setFen(Fen("fen with no auth"))
    val unauth = vm.state.first { it is ExplorerState.Unauthorized } as ExplorerState.Unauthorized
    unauth.source shouldBe ExplorerSource.MASTERS
  }

  private val startFen = GameEngine().toFen()
  private val afterE4Fen = GameEngine().apply { playSanMove("e4") }.toFen()
  private val startResponse =
    LichessExplorerResponse(white = 10, draws = 5, black = 3, moves = emptyList())

  /** A view model whose client has no token, so only seeded positions ever load. */
  private fun signedOutViewModel(
    cache: InMemoryExplorerCache,
    scope: CoroutineScope,
    debounce: Duration = 0.milliseconds,
  ): ExplorerViewModel {
    val client =
      LichessExplorerClient(
        httpClient = HttpClient(MockEngine { _ -> respond("", HttpStatusCode.ServiceUnavailable) }),
        tokenProvider = { null },
        minGap = 0.milliseconds,
      )
    return ExplorerViewModel(CachedExplorer(client, cache), scope, debounce = debounce)
  }

  /** A signed-in view model whose every network request answers [status]. */
  private fun failingViewModel(
    status: HttpStatusCode,
    cache: InMemoryExplorerCache,
    scope: CoroutineScope,
  ): ExplorerViewModel {
    val client =
      LichessExplorerClient(
        httpClient = HttpClient(MockEngine { _ -> respond("", status) }),
        tokenProvider = { LichessToken("test-token") },
        minGap = 0.milliseconds,
        maxAttemptsOn429 = 1,
      )
    return ExplorerViewModel(CachedExplorer(client, cache), scope, debounce = 0.milliseconds)
  }

  private fun seededCache(): InMemoryExplorerCache =
    InMemoryExplorerCache().apply { seed(startFen, ExplorerSource.MASTERS, startResponse) }

  @Test
  fun noLoadYetIsHidden() = runTest {
    val vm = signedOutViewModel(InMemoryExplorerCache(), backgroundScope)

    vm.setFen(startFen)
    vm.state.first { it is ExplorerState.Unauthorized }

    vm.positionStats.value shouldBe PositionStatsState.Hidden
  }

  @Test
  fun cachedPositionIsShownCurrent() = runTest {
    val vm = signedOutViewModel(seededCache(), backgroundScope)

    vm.setFen(startFen)

    vm.positionStats.first { it is PositionStatsState.Shown } shouldBe
      PositionStatsState.Shown(ExplorerSource.MASTERS, startResponse, isCurrent = true)
  }

  @Test
  fun signedOutUncachedPositionKeepsPreviousStatsDimmed() = runTest {
    val vm = signedOutViewModel(seededCache(), backgroundScope)
    vm.setFen(startFen)
    vm.positionStats.first { it is PositionStatsState.Shown }

    vm.setFen(afterE4Fen)
    vm.state.first { it is ExplorerState.Unauthorized }

    vm.positionStats.first { it is PositionStatsState.Shown && !it.isCurrent } shouldBe
      PositionStatsState.Shown(ExplorerSource.MASTERS, startResponse, isCurrent = false)
  }

  @Test
  fun returningToCachedPositionIsCurrentAgain() = runTest {
    val vm = signedOutViewModel(seededCache(), backgroundScope)
    vm.setFen(startFen)
    vm.positionStats.first { it is PositionStatsState.Shown }
    vm.setFen(afterE4Fen)
    vm.state.first { it is ExplorerState.Unauthorized }

    vm.setFen(startFen)

    vm.positionStats.first { it is PositionStatsState.Shown && it.isCurrent } shouldBe
      PositionStatsState.Shown(ExplorerSource.MASTERS, startResponse, isCurrent = true)
  }

  @Test
  fun sourceSwitchKeepsOldSourceUntilNewNumbersArrive() = runTest {
    val vm = signedOutViewModel(seededCache(), backgroundScope)
    vm.setFen(startFen)
    vm.positionStats.first { it is PositionStatsState.Shown }

    vm.setSource(ExplorerSource.LICHESS)
    vm.state.first { it is ExplorerState.Unauthorized }

    vm.positionStats.first { it is PositionStatsState.Shown && !it.isCurrent } shouldBe
      PositionStatsState.Shown(ExplorerSource.MASTERS, startResponse, isCurrent = false)
  }

  @Test
  fun zeroGameResponseIsShown() = runTest {
    val empty = LichessExplorerResponse(white = 0, draws = 0, black = 0, moves = emptyList())
    val cache = InMemoryExplorerCache().apply { seed(startFen, ExplorerSource.MASTERS, empty) }
    val vm = signedOutViewModel(cache, backgroundScope)

    vm.setFen(startFen)

    vm.positionStats.first { it is PositionStatsState.Shown } shouldBe
      PositionStatsState.Shown(ExplorerSource.MASTERS, empty, isCurrent = true)
  }

  @Test
  fun steppingBetweenCachedPositionsNeverDims() = runTest {
    val cache = seededCache().apply { seed(afterE4Fen, ExplorerSource.MASTERS, startResponse) }
    val vm = signedOutViewModel(cache, backgroundScope, debounce = 150.milliseconds)
    val seen = mutableListOf<PositionStatsState>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      vm.positionStats.toList(seen)
    }
    vm.setFen(startFen)
    vm.positionStats.first { it is PositionStatsState.Shown }

    vm.setFen(afterE4Fen)
    advanceTimeBy(1_000.milliseconds)

    seen.filterIsInstance<PositionStatsState.Shown>().none { !it.isCurrent } shouldBe true
  }

  @Test
  fun staleCachedPositionIsShownCurrent() = runTest {
    val cache =
      InMemoryExplorerCache().apply {
        seed(startFen, ExplorerSource.LICHESS, startResponse, DateUtil.now() - 30.days)
      }
    val vm = signedOutViewModel(cache, backgroundScope)

    vm.setSource(ExplorerSource.LICHESS)
    vm.setFen(startFen)

    vm.positionStats.first { it is PositionStatsState.Shown } shouldBe
      PositionStatsState.Shown(ExplorerSource.LICHESS, startResponse, isCurrent = true)
  }

  @Test
  fun rateLimitedBeforeAnyLoadIsHidden() = runTest {
    val vm =
      failingViewModel(HttpStatusCode.TooManyRequests, InMemoryExplorerCache(), backgroundScope)

    vm.setFen(startFen)
    vm.state.first { it is ExplorerState.RateLimited }

    vm.positionStats.value shouldBe PositionStatsState.Hidden
  }

  @Test
  fun networkErrorAfterLoadKeepsPreviousStatsDimmed() = runTest {
    val vm = failingViewModel(HttpStatusCode.InternalServerError, seededCache(), backgroundScope)
    vm.setFen(startFen)
    vm.positionStats.first { it is PositionStatsState.Shown }

    vm.setFen(afterE4Fen)
    vm.state.first { it is ExplorerState.Error }

    vm.positionStats.first { it is PositionStatsState.Shown && !it.isCurrent } shouldBe
      PositionStatsState.Shown(ExplorerSource.MASTERS, startResponse, isCurrent = false)
  }
}
