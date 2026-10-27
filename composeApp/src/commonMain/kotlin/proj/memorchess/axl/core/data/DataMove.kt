package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

/** Move that can be stored in [DatabaseQueryManager] */
data class DataMove(
  /** Origin position of the move */
  val origin: PositionKey,

  /** Destination position of the move */
  val destination: PositionKey,

  /** The move in standard notation */
  val move: String,

  /**
   * Whether the move has to be learned: the effective marking, derived from [ownIsGood] and the
   * markings of the installed repertoires holding the move. Never synced.
   *
   * A bad move is a mistake. It is still saved because the user has to learn how to counter it.
   */
  val isGood: Boolean? = null,

  /**
   * The user's own marking, or `null` when the user does not hold the move manually. The sync stamps
   * below version this manual hold.
   */
  val ownIsGood: Boolean? = null,

  /** Whether the move has been deleted. */
  val isDeleted: Boolean = false,

  /**
   * Date at which this move was first added.
   *
   * Excluded from equality like [updatedAt]: two moves describing the same transition are the same
   * move regardless of when they were recorded.
   */
  val createdAt: Instant = DateUtil.now(),

  /** Date at which this move was updated */
  val updatedAt: Instant = DateUtil.now(),

  /** Device that wrote this version. Stamped by [proj.memorchess.axl.core.graph.TreeStore]. */
  val originDevice: String = "",

  /** That device's write counter at the time. */
  val deviceSeq: Long = 0L,
) {

  override fun equals(other: Any?) =
    other is DataMove && EssentialData(this) == EssentialData(other)

  override fun hashCode() = EssentialData(this).hashCode()

  override fun toString() =
    EssentialData(this).toString().replaceFirst("EssentialData", "StoredMove")

  private data class EssentialData(
    val origin: PositionKey,
    val destination: PositionKey,
    val move: String,
    val isGood: Boolean?,
    val ownIsGood: Boolean?,
    val isDeleted: Boolean,
  ) {
    constructor(
      storedNode: DataMove
    ) : this(
      storedNode.origin,
      storedNode.destination,
      storedNode.move,
      storedNode.isGood,
      storedNode.ownIsGood,
      storedNode.isDeleted,
    )
  }
}
