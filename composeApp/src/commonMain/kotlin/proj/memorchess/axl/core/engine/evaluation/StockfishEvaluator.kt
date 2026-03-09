package proj.memorchess.axl.core.engine.evaluation

import co.touchlab.kermit.Logger
import fr.axl_lvy.stockfish_multiplatform.Score
import fr.axl_lvy.stockfish_multiplatform.StockfishEngine
import fr.axl_lvy.stockfish_multiplatform.getStockfish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wraps the Stockfish engine and exposes position evaluation as a [StateFlow].
 *
 * Each call to [evaluate] cancels the previous search and starts a new one. The evaluation is
 * normalized to White's perspective. Access to the native engine is serialized via a [Mutex] to
 * prevent concurrent native calls that would cause a segfault.
 */
class StockfishEvaluator {

  private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private var engine: StockfishEngine? = null
  private var searchJob: Job? = null
  private var initJob: Job? = null
  private val engineMutex = Mutex()

  private val _evaluation = MutableStateFlow<EvaluationScore?>(null)

  /** Current evaluation, or `null` if unavailable. */
  val evaluation: StateFlow<EvaluationScore?> = _evaluation.asStateFlow()

  init {
    initJob =
      scope.launch {
        try {
          engine = getStockfish()
        } catch (e: Exception) {
          Logger.w(TAG) { "Stockfish not available on this platform: ${e.message}" }
        }
      }
  }

  /**
   * Evaluates the given FEN position. Cancels any in-progress search.
   *
   * @param fen Full 6-part FEN string.
   * @param isBlackToMove `true` when it is Black's turn, used to flip the score to White's
   *   perspective.
   */
  suspend fun evaluate(fen: String, isBlackToMove: Boolean) {
    val currentEngine = awaitEngine() ?: return

    // Cancel previous search and wait for it to fully finish before touching the engine again.
    searchJob?.let {
      currentEngine.stop()
      it.join()
    }

    searchJob =
      scope.launch {
        engineMutex.withLock {
          try {
            currentEngine.setPosition(fen = fen)
            currentEngine.search(depth = SEARCH_DEPTH) { info ->
              val score = info.score ?: return@search
              _evaluation.value = toEvaluationScore(score, isBlackToMove)
            }
          } catch (e: Exception) {
            Logger.w(TAG) { "Evaluation failed: ${e.message}" }
          }
        }
      }
  }

  /** Stops the engine and cancels the coroutine scope. */
  fun close() {
    searchJob?.cancel()
    initJob?.cancel()
    try {
      engine?.close()
    } catch (e: Exception) {
      Logger.w(TAG) { "Error closing Stockfish: ${e.message}" }
    }
  }

  private suspend fun awaitEngine(): StockfishEngine? {
    initJob?.join()
    return engine
  }

  companion object {
    private const val TAG = "StockfishEvaluator"
    private const val SEARCH_DEPTH = 20

    /**
     * Converts a library [Score] to an [EvaluationScore] from White's perspective.
     *
     * The library reports scores from the side-to-move's perspective, so we flip when Black is to
     * move.
     */
    private fun toEvaluationScore(score: Score, isBlackToMove: Boolean): EvaluationScore {
      val flip = if (isBlackToMove) -1 else 1
      return when (score) {
        is Score.Cp -> EvaluationScore.Centipawns(score.centipawns * flip)
        is Score.Mate -> EvaluationScore.Mate(score.moves * flip)
      }
    }
  }
}
