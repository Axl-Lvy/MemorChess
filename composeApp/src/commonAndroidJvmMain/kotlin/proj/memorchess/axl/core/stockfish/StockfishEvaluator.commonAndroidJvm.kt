package proj.memorchess.axl.core.stockfish

import com.diamondedge.logging.logging
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import proj.memorchess.axl.core.data.PositionIdentifier

private val LOGGER = logging()

actual class StockfishEvaluator {
  init {
    UCI.uci()
  }

  actual suspend fun evaluate(position: PositionIdentifier) {
    val realFen = position.toRealFen()
    LOGGER.info { "Evaluating $realFen" }
    mutableEvaluation.value = 0.0
    UCI.stop()
    UCI.newGame()
    delay(1.seconds)
    if (UCI.position("fen " + realFen)) {
      LOGGER.info { "Evaluating..." }
      val evaluationOnNewDepth = EvaluationOnNewDepth(mutableEvaluation)
      UCI.setOutputListener(evaluationOnNewDepth)
      UCI.go("depth 10")
    } else {
      LOGGER.error { "Failed to set position: $realFen" }
    }
  }

  private val mutableEvaluation = MutableStateFlow(0.0)
  actual val evaluation: StateFlow<Double> get() = mutableEvaluation
}

private class EvaluationOnNewDepth(private val mutableEvaluation: MutableStateFlow<Double>) :
  OutputListener {
  private var currentDepth = 0

  override fun onOutput(output: String) {
    LOGGER.info {output}
    if (output.startsWith("bestmove")) {
      evaluate()
    } else {
      val splitLog = output.split(" ")
      if (splitLog.size < 3 || splitLog[2].any { !it.isDigit() }) {
        return
      }
      val depth = splitLog[2].toIntOrNull() ?: return
      if (depth > currentDepth) {
        evaluate()
      }
    }
  }

  private fun evaluate() {
    mutableEvaluation.value = UCI.scores().total
  }
}
