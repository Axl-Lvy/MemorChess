package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardPhase

/**
 * Entity representing a [DataNode] ready to be stored in the database.
 *
 * Scheduling fields are owned by the active
 * [proj.memorchess.axl.core.scheduling.SchedulingAlgorithm]. Wave A persists the full FSRS card
 * state on every node so that scheduling decisions are stable across sessions.
 *
 * @property positionKey FEN string uniquely identifying the chess position.
 * @property dueDate Moment when the card is next due for review.
 * @property lastReview Moment of the most recent review, or `null` for a brand new card.
 * @property firstReview Moment of the very first review, or `null` for a never reviewed card.
 * @property stability FSRS stability of the memory trace, in days.
 * @property difficulty FSRS card difficulty.
 * @property reps Total number of recorded reviews.
 * @property lapses Total number of times the card has been forgotten.
 * @property phase Name of the [proj.memorchess.axl.core.scheduling.CardPhase] in the FSRS state
 *   machine.
 * @property step Index into the active learning or relearning step ladder.
 * @property depth Minimum graph depth at which this position can be reached from the root.
 * @property isDeleted Soft delete flag.
 * @property updatedAt Last modification timestamp.
 */
@Entity(
  tableName = "NodeEntity",
  indices =
    [
      Index(value = ["dueDate"]),
      Index(value = ["depth"]),
      Index(value = ["isDeleted"]),
      Index(value = ["updatedAt"]),
    ],
)
data class NodeEntity(
  @PrimaryKey(autoGenerate = false) val positionKey: String,
  val dueDate: Instant = DateUtil.now(),
  val lastReview: Instant? = null,
  val firstReview: Instant? = null,
  val stability: Double = 0.0,
  val difficulty: Double = 0.0,
  val reps: Int = 0,
  val lapses: Int = 0,
  val phase: String = CardPhase.NEW.name,
  val step: Int = 0,
  val depth: Int,
  val isDeleted: Boolean = false,
  val updatedAt: Instant = DateUtil.now(),
)
