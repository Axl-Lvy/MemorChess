package proj.memorchess.axl.ui.components.board

import proj.memorchess.axl.core.engine.BoardLocation

/**
 * Training feedback the board's rendering layer needs together.
 *
 * @property playedSquare Where the last graded move landed, `null` before the first one.
 * @property correctSquare Where the good move should have landed; only set on a wrong attempt.
 * @property isCorrect Whether the move behind [attempt] was correct; `null` before any graded move,
 *   which [playedSquare] and [attempt] do not imply.
 * @property attempt Monotonic counter of graded moves; a change drives every animation once.
 * @property hintSquares Squares outlined by a hint or reveal until the next move is played.
 */
data class BoardTrainingFeedback(
  val playedSquare: BoardLocation? = null,
  val correctSquare: BoardLocation? = null,
  val isCorrect: Boolean? = null,
  val attempt: Int = 0,
  val hintSquares: Set<BoardLocation> = emptySet(),
)
