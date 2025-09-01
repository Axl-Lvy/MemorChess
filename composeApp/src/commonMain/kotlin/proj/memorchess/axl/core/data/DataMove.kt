package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.date.DateUtil

/** Move that can be stored in [DatabaseQueryManager] */
data class DataMove(
  /** Origin position of the move */
  val origin: PositionIdentifier,

  /** Destination position of the move */
  val destination: PositionIdentifier,

  /** The move in standard notation */
  val move: String,

  /**
   * Whether the move has to be learned.
   *
   * A bad move is a mistake. It is still saved because the user has to learn how to counter it.
   *
   * Bad moves are always isolated: previous and the next moves are good.
   */
  var isGood: Boolean? = null,

  /** Whether the move has been deleted. */
  val isDeleted: Boolean = false,

  /** Date at which this move was updated */
  val updatedAt: LocalDateTime = DateUtil.now(),
) {

  override fun equals(other: Any?) =
    other is DataMove && EssentialData(this) == EssentialData(other)

  override fun hashCode() = EssentialData(this).hashCode()

  override fun toString() =
    EssentialData(this).toString().replaceFirst("EssentialData", "StoredMove")

  private data class EssentialData(
    val origin: PositionIdentifier,
    val destination: PositionIdentifier,
    val move: String,
    var isGood: Boolean?,
    val isDeleted: Boolean,
  ) {
    constructor(
      storedNode: DataMove
    ) : this(
      storedNode.origin,
      storedNode.destination,
      storedNode.move,
      storedNode.isGood,
      storedNode.isDeleted,
    )
  }
}
