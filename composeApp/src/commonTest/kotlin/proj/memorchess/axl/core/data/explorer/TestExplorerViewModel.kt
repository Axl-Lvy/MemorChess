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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import proj.memorchess.axl.test_util.InMemoryExplorerCache

class TestExplorerViewModel {

  private val sampleJson =
    """
    {"white":1,"draws":0,"black":0,"moves":[],"opening":null}
    """
      .trimIndent()

  private fun buildClient() =
    LichessExplorerClient(
      tokenProvider = { "test-token" },
      httpClient =
        HttpClient(
          MockEngine { _ ->
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

  @Test
  fun setFenTriggersLoadingThenLoaded() = runTest {
    val cached = CachedExplorer(buildClient(), InMemoryExplorerCache())
    val vm = ExplorerViewModel(cached, scope = backgroundScope, debounce = 0.milliseconds)

    vm.setFen("test fen")
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

    vm.setFen("fen for switch")
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
        tokenProvider = { "test-token" },
        httpClient =
          HttpClient(
            MockEngine { _ -> respond(content = "", status = HttpStatusCode.InternalServerError) }
          ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          },
        minGap = 0.milliseconds,
      )
    val cached = CachedExplorer(errClient, InMemoryExplorerCache())
    val vm = ExplorerViewModel(cached, scope = backgroundScope, debounce = 0.milliseconds)

    vm.setFen("fen with error")
    val err = vm.state.first { it is ExplorerState.Error } as ExplorerState.Error
    err.source shouldBe ExplorerSource.MASTERS
  }
}
