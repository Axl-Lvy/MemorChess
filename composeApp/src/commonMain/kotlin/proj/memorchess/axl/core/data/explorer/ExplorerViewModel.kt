package proj.memorchess.axl.core.data.explorer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Drives the Lichess explorer panel from the FENs pushed through [setFen], debounced. [scope] must
 * be tied to the screen's lifecycle.
 */
@OptIn(FlowPreview::class)
class ExplorerViewModel(
  private val cachedExplorer: CachedExplorer,
  scope: CoroutineScope,
  debounce: Duration = DEFAULT_DEBOUNCE,
) {

  private val fenFlow = MutableStateFlow<String?>(null)
  private val sourceFlow = MutableStateFlow(ExplorerSource.MASTERS)
  private val internalState = MutableStateFlow<ExplorerState>(ExplorerState.Idle)

  /** Current state of the panel. */
  val state: StateFlow<ExplorerState> = internalState.asStateFlow()

  /** Currently selected source. */
  val source: StateFlow<ExplorerSource> = sourceFlow.asStateFlow()

  init {
    scope.launch {
      combine(fenFlow.filterNotNull().debounce(debounce), sourceFlow) { fen, source ->
          fen to source
        }
        .collectLatest { (fen, source) -> runFetch(fen, source) }
    }
  }

  /** Updates the FEN to query. Safe to call frequently; calls within [debounce] are coalesced. */
  fun setFen(fen: String) {
    fenFlow.value = fen
  }

  /** Switches the source. Triggers an immediate refetch for the latest known FEN. */
  fun setSource(source: ExplorerSource) {
    sourceFlow.value = source
  }

  private suspend fun runFetch(fen: String, source: ExplorerSource) {
    internalState.value = ExplorerState.Loading(fen, source)
    val result = cachedExplorer.fetch(source, fen)
    internalState.value =
      when (result) {
        is CachedExplorerResult.Fresh ->
          ExplorerState.Loaded(source = source, response = result.response, isStale = false)
        is CachedExplorerResult.Stale ->
          ExplorerState.Loaded(source = source, response = result.response, isStale = true)
        is CachedExplorerResult.RateLimited -> ExplorerState.RateLimited(source = source)
        is CachedExplorerResult.Unauthorized -> ExplorerState.Unauthorized(source = source)
        is CachedExplorerResult.NetworkError ->
          ExplorerState.Error(source = source, message = result.message)
      }
  }

  companion object {
    /** Default debounce between rapid FEN changes. */
    val DEFAULT_DEBOUNCE: Duration = 150.milliseconds
  }
}

/** State of the explorer panel. */
sealed class ExplorerState {
  /** No FEN pushed yet. */
  data object Idle : ExplorerState()

  /** A fetch is in progress for [fen] under [source]. */
  data class Loading(val fen: String, val source: ExplorerSource) : ExplorerState()

  /**
   * A response is available. [isStale] is `true` when the network refresh failed and the response
   * comes from a cached entry past its TTL.
   */
  data class Loaded(
    val source: ExplorerSource,
    val response: LichessExplorerResponse,
    val isStale: Boolean,
  ) : ExplorerState()

  /** No cached entry and Lichess rate limited the request. */
  data class RateLimited(val source: ExplorerSource) : ExplorerState()

  /** User is not signed in to Lichess, or the token was rejected. */
  data class Unauthorized(val source: ExplorerSource) : ExplorerState()

  /** No cached entry and the network call failed. */
  data class Error(val source: ExplorerSource, val message: String) : ExplorerState()
}
