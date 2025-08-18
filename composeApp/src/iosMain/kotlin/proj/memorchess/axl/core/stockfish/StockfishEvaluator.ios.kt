package proj.memorchess.axl.core.stockfish

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import proj.memorchess.axl.core.data.PositionIdentifier

actual class StockfishEvaluator {
  actual suspend fun evaluate(position: PositionIdentifier) {
    // Not implemented yet
  }

  actual val evaluation: StateFlow<String>
    get() = MutableStateFlow("0.0")
}
