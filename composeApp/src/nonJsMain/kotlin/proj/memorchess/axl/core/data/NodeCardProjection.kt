package proj.memorchess.axl.core.data

import kotlin.time.Instant

/**
 * Lightweight Room projection holding only the columns a
 * [proj.memorchess.axl.core.graph.TrainingEntry] needs. It never loads edges, keeping the bounded
 * scheduling queries cheap.
 *
 * @property positionKey FEN string identifying the position.
 * @property dueDate Moment the card is next due.
 * @property lastReview Moment of the most recent review, or `null` for a brand new card.
 * @property firstReview Moment of the very first review, or `null` for a never reviewed card.
 * @property stability FSRS stability of the memory trace, in days.
 * @property difficulty FSRS card difficulty.
 * @property reps Total number of recorded reviews.
 * @property lapses Total number of times the card has been forgotten.
 * @property phase Name of the [proj.memorchess.axl.core.scheduling.CardPhase].
 * @property step Index into the active learning or relearning step ladder.
 */
data class NodeCardProjection(
  val positionKey: String,
  val dueDate: Instant,
  val lastReview: Instant?,
  val firstReview: Instant?,
  val stability: Double,
  val difficulty: Double,
  val reps: Int,
  val lapses: Int,
  val phase: String,
  val step: Int,
)
