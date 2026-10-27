package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardPhase

/**
 * Room row for a [DataNode], carrying its full FSRS card state.
 *
 * @property positionKey FEN string identifying the position.
 * @property lastReview Most recent review, or `null` for a brand new card.
 * @property firstReview First review, or `null` for a never reviewed card.
 * @property stability FSRS stability of the memory trace, in days.
 * @property phase Name of the [CardPhase].
 * @property step Index into the active learning or relearning step ladder.
 * @property depth Minimum graph depth at which this position is reached from the root.
 * @property hasGoodOutgoing `true` iff at least one non deleted outgoing edge is marked good.
 * @property createdAt When the position was first added; the new card tiebreak after [depth].
 * @property originDevice Device that wrote this version.
 * @property deviceSeq That device's write counter at the time.
 */
@Entity(
  tableName = "NodeEntity",
  indices =
    [
      Index(value = ["isDeleted"]),
      Index(value = ["updatedAt"]),
      Index(value = ["firstReview"]),
      Index(value = ["lastReview"]),
      Index(value = ["hasGoodOutgoing", "phase", "dueDate", "depth", "createdAt"]),
      Index(value = ["hasGoodOutgoing", "phase", "dueDate"]),
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
  val hasGoodOutgoing: Boolean = false,
  val createdAt: Instant = DateUtil.now(),
  val isDeleted: Boolean = false,
  val updatedAt: Instant = DateUtil.now(),
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
)
