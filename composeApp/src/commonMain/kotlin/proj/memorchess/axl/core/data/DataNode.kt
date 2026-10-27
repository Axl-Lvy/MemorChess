package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardState

/**
 * A position node as stored in the database. Equality only compares the position, moves, card state
 * and deletion flag.
 *
 * @property depth Minimum depth from the root; [Int.MAX_VALUE] when unset so new card ordering puts
 *   it last.
 * @property hasGoodOutgoing Whether a live outgoing edge is marked good. Recomputed on every write,
 *   so a supplied value only matters for a node with no moves yet.
 * @property createdAt When the position was first added; breaks new card ordering ties after
 *   [depth].
 */
data class DataNode(
  val positionKey: PositionKey,
  val previousAndNextMoves: PreviousAndNextMoves,
  val cardState: CardState,
  val depth: Int = Int.MAX_VALUE,
  val updatedAt: Instant = DateUtil.now(),
  val isDeleted: Boolean = false,
  val hasGoodOutgoing: Boolean = false,
  val createdAt: Instant = DateUtil.now(),
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
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
