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
 * @property hasGoodOutgoing Derived projection owned by [proj.memorchess.axl.core.graph.TreeStore]:
 *   `true` iff this node has at least one non deleted outgoing edge marked good. Maintained on
 *   every write and excluded from equality because it is recomputed from the node's outgoing edges.
 * @property createdAt Derived projection owned by [proj.memorchess.axl.core.graph.TreeStore]: the
 *   moment the position was first added, taken from the earliest non deleted incoming edge. Used as
 *   the new card ordering tiebreak after [depth] and excluded from equality like [updatedAt].
 * @constructor Creates a new node.
 */
data class DataNode(
  val positionKey: PositionKey,
  val previousAndNextMoves: PreviousAndNextMoves,
  val cardState: CardState,
  val depth: Int = 0,
  val updatedAt: Instant = DateUtil.now(),
  val isDeleted: Boolean = false,
  val hasGoodOutgoing: Boolean = false,
  val createdAt: Instant = DateUtil.now(),
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
