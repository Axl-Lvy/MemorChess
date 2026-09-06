package proj.memorchess.axl.ui.components.board

import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.ui.components.training.BoardContainer

/**
 * Bundles the training-feedback values that must reach the board's rendering layer together,
 * instead of threading each one as its own parameter down [BoardContainer] → [Board] → [BoardGrid].
 *
 * @property playedSquare The square the last graded move landed on, or `null` before the first move
 *   of the session.
 * @property correctSquare The square the good move should have landed on, or `null` before the
 *   first move of the session. Only ever set on a wrong attempt.
 * @property isCorrect Whether the move that produced the current [attempt] was correct.
 * @property attempt Monotonic counter of graded moves; a change drives every animation once.
 */
data class BoardTrainingFeedback(
  val playedSquare: BoardLocation? = null,
  val correctSquare: BoardLocation? = null,
  val isCorrect: Boolean = true,
  val attempt: Int = 0,
)
