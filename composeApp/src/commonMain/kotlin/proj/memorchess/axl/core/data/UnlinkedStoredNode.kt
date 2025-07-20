package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.date.PreviousAndNextDate

/**
 * A node without its linked moves.
 *
 * @property positionIdentifier The position
 * @property previousAndNextTrainingDate The date when this node was last trained and when it should
 * @property depth The minimum depth of the node in the graph
 * @property isDeleted Whether the node has been deleted
 */
data class UnlinkedStoredNode(
  val positionIdentifier: PositionIdentifier,
  val previousAndNextTrainingDate: PreviousAndNextDate,
  val depth: Int,
  val isDeleted: Boolean,
  val updatedAt: LocalDateTime,
)
