package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.PreviousAndNextMoves

/**
 * Data class representing a node in the database.
 *
 * @property positionKey The position.
 * @property previousAndNextMoves The linked moves.
 * @property previousAndNextTrainingDate The date when this node was last trained and when it should
 *   be trained next.
 * @property depth The minimum depth at which this position can be reached from the root.
 * @constructor Creates a new node.
 */
data class DataNode(
  val positionKey: PositionKey,
  val previousAndNextMoves: PreviousAndNextMoves,
  val previousAndNextTrainingDate: PreviousAndNextDate,
  val depth: Int = 0,
  val updatedAt: Instant = DateUtil.now(),
  val isDeleted: Boolean = false,
) {

  override fun equals(other: Any?) =
    other is DataNode && EssentialData(this) == EssentialData(other)

  override fun hashCode() = EssentialData(this).hashCode()

  override fun toString() =
    EssentialData(this).toString().replaceFirst("EssentialData", "StoredNode")

  private data class EssentialData(
    val positionKey: PositionKey,
    val previousAndNextMoves: PreviousAndNextMoves,
    val previousAndNextTrainingDate: PreviousAndNextDate,
    val isDeleted: Boolean,
  ) {
    constructor(
      dataNode: DataNode
    ) : this(
      dataNode.positionKey,
      dataNode.previousAndNextMoves,
      dataNode.previousAndNextTrainingDate,
      dataNode.isDeleted,
    )
  }
}
