package proj.memorchess.axl.core.stockfish

import kotlinx.coroutines.flow.StateFlow
import proj.memorchess.axl.core.data.PositionIdentifier

expect class StockfishEvaluator() {

  /**
   * Evaluates the given position using Stockfish.
   *
   * @param position The position to evaluate.
   * @return The evaluation score for the position.
   */
  suspend fun evaluate(position: PositionIdentifier)

  val evaluation: StateFlow<Double>
}
