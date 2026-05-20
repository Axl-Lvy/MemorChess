package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Data class representing a node in the database.
 *
 * @property positionKey The position.
 * @property previousAndNextMoves The linked moves.
 * @property cardState Scheduling state used by the active
 *   [proj.memorchess.axl.core.scheduling.SchedulingAlgorithm]. Holds the next due date and any
 *   algorithm specific state such as FSRS stability and difficulty.
 * @property depth The minimum depth at which this position can be reached from the root.
 * @constructor Creates a new node.
 */
data class DataNode(
  val positionKey: PositionKey,
  val previousAndNextMoves: PreviousAndNextMoves,
  val cardState: CardState,
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
    val cardState: CardState,
    val isDeleted: Boolean,
  ) {
    constructor(
      dataNode: DataNode
    ) : this(
      dataNode.positionKey,
      dataNode.previousAndNextMoves,
      dataNode.cardState,
      dataNode.isDeleted,
    )
  }
}
