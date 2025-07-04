package proj.memorchess.axl.core.data

import com.diamondedge.logging.logging

/** Move that can be stored in [ICommonDatabase] */
data class StoredMove(
  /** Origin position of the move */
  val origin: PositionKey,

  /** Destination position of the move */
  val destination: PositionKey,

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
) {
  /** Saves the move to the database */
  suspend fun save() {
    LOGGER.info { "Saving $this" }
    DatabaseHolder.getDatabase().insertMove(this)
  }

  override fun toString(): String {
    return "StoredMove(move='$move', isGood=$isGood, origin=$origin, destination=$destination)"
  }
}

private val LOGGER = logging()
