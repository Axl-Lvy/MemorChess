package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.date.DateUtil

/**
 * Room entity representing an [StoredMove].
 *
 * @property origin FEN string of the origin position.
 * @property destination FEN string of the destination position.
 * @property move The move string (e.g., "e4").
 * @property isGood Indicates whether the move has to be learned.
 */
@Entity(
  tableName = "MoveEntity",
  primaryKeys = ["origin", "destination"],
  indices =
    [
      Index(value = ["isGood"]),
      Index(value = ["origin"]),
      Index(value = ["destination"]),
      Index(value = ["isDeleted"]),
      Index(value = ["updatedAt"]),
    ],
)
data class MoveEntity(
  val origin: String,
  val destination: String,
  val move: String,
  val isGood: Boolean,

  /** If true, the node is deleted. */
  val isDeleted: Boolean = false,

  /** The date time of the last update. */
  val updatedAt: LocalDateTime = DateUtil.now(),
) {

  /** Converts to an [StoredMove]. */
  fun toStoredMove(): StoredMove {
    return StoredMove(
      PositionIdentifier(origin),
      PositionIdentifier(destination),
      move,
      isGood,
      isDeleted,
    )
  }

  companion object {
    /** Converts an [StoredMove] to a [MoveEntity]. */
    fun convertToEntity(storedMove: StoredMove): MoveEntity {
      val isGood = storedMove.isGood
      checkNotNull(isGood) {
        "A StoredMove must have a isGood value to be inserted into the database"
      }
      return MoveEntity(
        storedMove.origin.fenRepresentation,
        storedMove.destination.fenRepresentation,
        storedMove.move,
        isGood,
      )
    }
  }
}
