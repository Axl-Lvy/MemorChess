package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate

/** Position metadata stored in [DatabaseQueryManager]. */
data class DataPosition(
  /** The position. */
  val positionIdentifier: PositionIdentifier,

  /** Minimum depth in the opening tree where this position exists. */
  val depth: Int,

  /** The date when this position was last trained and when it should be trained next. */
  val previousAndNextTrainingDate: PreviousAndNextDate,

  /** Date at which this position was updated. */
  val updatedAt: Instant = DateUtil.now(),

  /** Whether the position has been deleted. */
  val isDeleted: Boolean = false,
)
