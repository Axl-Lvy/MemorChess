package proj.memorchess.axl.core.data

import kotlin.time.Instant

/**
 * Room projection holding only the columns a [proj.memorchess.axl.core.graph.TrainingEntry] needs,
 * so scheduling queries never load edges.
 *
 * @property lastReview Most recent review, or `null` for a brand new card.
 * @property firstReview First review, or `null` for a never reviewed card.
 * @property stability FSRS stability of the memory trace, in days.
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
