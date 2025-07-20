package proj.memorchess.axl.core.data

import com.diamondedge.logging.logging
import kotlinx.datetime.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.date.DateUtil

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
  val updatedAt: LocalDateTime = DateUtil.now(),
) : KoinComponent {

  private val db by inject<DatabaseQueryManager>()

  /** Saves the move to the database */
  suspend fun save() {
    LOGGER.info { "Saving $this" }
    db.insertMove(this)
  }

  override fun equals(other: Any?) =
    other is StoredMove && EssentialData(this) == EssentialData(other)

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
      storedNode: StoredMove
    ) : this(
      storedNode.origin,
      storedNode.destination,
      storedNode.move,
      storedNode.isGood,
      storedNode.isDeleted,
    )
  }
}

private val LOGGER = logging()
