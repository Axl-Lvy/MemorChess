package proj.memorchess.axl.core.engine.evaluation

import co.touchlab.kermit.Logger
import fr.axl_lvy.stockfish_multiplatform.Score
import fr.axl_lvy.stockfish_multiplatform.StockfishEngine
import fr.axl_lvy.stockfish_multiplatform.getStockfish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Wraps the Stockfish engine and exposes position evaluation as a [StateFlow].
 *
 * Each call to [evaluate] cancels the previous search and starts a new one. The evaluation is
 * normalized to White's perspective.
 *
 * @param maxDepth Maximum search depth (5–25), or 0 for infinite.
 */
class StockfishEvaluator(private val maxDepth: Int = DEFAULT_SEARCH_DEPTH) {

  private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private var engine: StockfishEngine? = null
  private var searchJob: Job? = null
  private var initJob: Job? = null

  private val _evaluation = MutableStateFlow<EvaluationScore?>(null)

  /** Current evaluation, or `null` if unavailable. */
  val evaluation: StateFlow<EvaluationScore?> = _evaluation.asStateFlow()

  private val _currentDepth = MutableStateFlow<Int?>(null)

  /** Depth reached by the engine during the current search, or `null` if idle. */
  val currentDepth: StateFlow<Int?> = _currentDepth.asStateFlow()

  private val _bestMove = MutableStateFlow<BestMove?>(null)

  /** The engine's recommended best move, or `null` while searching or unavailable. */
  val bestMove: StateFlow<BestMove?> = _bestMove.asStateFlow()

  init {
    initJob =
      scope.launch {
        try {
          engine = getStockfish()
        } catch (e: Exception) {
          Logger.w(tag = TAG, throwable = e) {
            "Stockfish not available on this platform: ${e.message}"
          }
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

    _currentDepth.value = null
    _bestMove.value = null

    searchJob =
      scope.launch {
        try {
          currentEngine.setPosition(fen = fen)
          val depthArg = if (maxDepth == 0) null else maxDepth
          val result =
            currentEngine.search(depth = depthArg) { info ->
              info.depth?.let { _currentDepth.value = it }
              val score = info.score ?: return@search
              _evaluation.value = toEvaluationScore(score, isBlackToMove)
              if (info.pv.isNotEmpty()) {
                _bestMove.value = BestMove.fromUci(info.pv.first())
              }
            }
          _bestMove.value = BestMove.fromUci(result.bestMove)
        } catch (e: Exception) {
          Logger.w(tag = TAG, throwable = e) { "Evaluation failed: ${e.message}" }
        }
      }
  }

  /** Stops the engine and cancels the coroutine scope. */
  fun close() {
    scope.cancel()
    try {
      engine?.close()
    } catch (e: Exception) {
      Logger.w(tag = TAG, throwable = e) { "Error closing Stockfish: ${e.message}" }
    }
  }

  private suspend fun awaitEngine(): StockfishEngine? {
    initJob?.join()
    return engine
  }

  companion object {
    private const val TAG = "StockfishEvaluator"
    private const val DEFAULT_SEARCH_DEPTH = 20

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
