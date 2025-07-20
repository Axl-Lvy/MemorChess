package proj.memorchess.axl.core.data

import com.diamondedge.logging.logging
import kotlinx.datetime.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Move that can be stored in [DatabaseQueryManager] */
data class StoredMove(
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
  val updatedAt: LocalDateTime? = null,
) : KoinComponent {

  private val db by inject<DatabaseQueryManager>()

  /** Saves the move to the database */
  suspend fun save() {
    LOGGER.info { "Saving $this" }
    db.insertMove(this)
  }

  override fun toString(): String {
    return "StoredMove(move='$move', isGood=$isGood, origin=$origin, destination=$destination, isDeleted=$isDeleted)"
  }
}

private val LOGGER = logging()
