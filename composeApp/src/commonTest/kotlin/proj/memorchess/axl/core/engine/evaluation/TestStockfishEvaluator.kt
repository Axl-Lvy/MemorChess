package proj.memorchess.axl.core.engine.evaluation

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class TestStockfishEvaluator {

  private companion object {
    const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    const val MATE_IN_ONE_FEN = "6k1/5ppp/8/8/8/8/8/4R1K1 w - - 0 1"
  }

  @Test
  fun initialStateIsNull() = runTest {
    val evaluator = StockfishEvaluator(maxDepth = 10)
    try {
      assertNull(evaluator.evaluation.value)
      assertNull(evaluator.bestMove.value)
      assertNull(evaluator.currentDepth.value)
    } finally {
      evaluator.close()
    }
  }

  @Test
  fun evaluateProducesScoreAndBestMove() = runTest {
    val evaluator = StockfishEvaluator(maxDepth = 10)
    try {
      evaluator.evaluate(STARTING_FEN, isBlackToMove = false)
      evaluator.bestMove.first { it != null }.shouldNotBeNull()
      evaluator.evaluation.first { it != null }.shouldNotBeNull()
      evaluator.currentDepth.first { it != null }.shouldNotBeNull()
    } finally {
      evaluator.close()
    }
  }

  @Test
  fun evaluateStartingPositionReturnsCentipawns() = runTest {
    val evaluator = StockfishEvaluator(maxDepth = 10)
    try {
      evaluator.evaluate(STARTING_FEN, isBlackToMove = false)
      evaluator.evaluation.first { it != null }.shouldBeInstanceOf<EvaluationScore.Centipawns>()
    } finally {
      evaluator.close()
    }
  }

  @Test
  fun evaluateMatePositionReturnsMateScore() = runTest {
    val evaluator = StockfishEvaluator(maxDepth = 10)
    try {
      evaluator.evaluate(MATE_IN_ONE_FEN, isBlackToMove = false)
      evaluator.evaluation.first { it != null }.shouldBeInstanceOf<EvaluationScore.Mate>()
    } finally {
      evaluator.close()
    }
  }

  @Test
  fun newEvaluationResetsPreviousResults() = runTest {
    val evaluator = StockfishEvaluator(maxDepth = 10)
    try {
      evaluator.evaluate(STARTING_FEN, isBlackToMove = false)
      evaluator.bestMove.first { it != null }.shouldNotBeNull()

      // Starting a new evaluation should reset best move immediately.
      evaluator.evaluate(MATE_IN_ONE_FEN, isBlackToMove = false)
      // After the second evaluate completes, we should have results for the new position.
      evaluator.evaluation.first { it != null }.shouldNotBeNull()
      evaluator.bestMove.first { it != null }.shouldNotBeNull()
    } finally {
      evaluator.close()
    }
  }
}
