package proj.memorchess.axl.core.stockfish

import com.diamondedge.logging.logging
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import proj.memorchess.axl.core.data.PositionIdentifier

private val LOGGER = logging()

actual class StockfishEvaluator {
  init {
    UCI.uci()
    val numberOfCpu = Runtime.getRuntime().availableProcessors().reservedForStockfish()
    LOGGER.info { "Using $numberOfCpu CPU cores for Stockfish evaluation" }
    UCI.setOption("Threads", numberOfCpu.toString())
  }

  actual suspend fun evaluate(position: PositionIdentifier) {
    val realFen = position.toRealFen()
    LOGGER.info { "Evaluating $realFen" }
    mutableEvaluation.value = "0.0"
    UCI.stop()
    UCI.newGame()
    if (UCI.position("fen $realFen")) {
      LOGGER.info { "Evaluating..." }
      val evaluationOutputListener = EvaluationOutputListener(mutableEvaluation)
      UCI.setOutputListener(evaluationOutputListener)
      UCI.go("depth 20")
    } else {
      LOGGER.error { "Failed to set position: $realFen" }
    }
  }

  private val mutableEvaluation = MutableStateFlow("0.0")
  actual val evaluation: StateFlow<String>
    get() = mutableEvaluation
}

private class EvaluationOutputListener(private val mutableEvaluation: MutableStateFlow<String>) :
  OutputListener {

  private val lastUpdateTime = AtomicLong(0)
  private val updateIntervalNs = 100_000_000L // 100ms in nanoseconds

  override fun onOutput(output: String) {
    val now = System.nanoTime()
    val last = lastUpdateTime.get()
    if (now - last >= updateIntervalNs) {
      if (lastUpdateTime.compareAndSet(last, now)) {
        LOGGER.info { output }

        if (output.startsWith("info") && output.contains("score")) {
          val parts = output.split(" ")

          val scoreIndex = parts.indexOf("cp")
          val mateIndex = parts.indexOf("mate")

          val eval: String? =
            when {
              scoreIndex != -1 && scoreIndex + 1 < parts.size -> {
                val cp = parts[scoreIndex + 1].toIntOrNull()
                cp?.let { "%.2f".format(it / 100.0) } // e.g. "0.34"
              }
              mateIndex != -1 && mateIndex + 1 < parts.size -> {
                val mate = parts[mateIndex + 1].toIntOrNull()
                mate?.let { "M$it" } // e.g. "M3" or "M-2"
              }
              else -> null
            }
          if (eval != null) {
            mutableEvaluation.value = eval
          }
        }
      }
    }
  }
}

private fun Int.reservedForStockfish(): Int {
  return when (this) {
    1 -> 1
    2 -> 1
    3 -> 2
    else -> this - 2
  }
}
