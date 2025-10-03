package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

/**
 * Room entity representing an [DataMove].
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
  val updatedAt: Instant = DateUtil.now(),
) {

  /** Converts to an [DataMove]. */
  fun toStoredMove(): DataMove {
    return DataMove(
      PositionIdentifier(origin),
      PositionIdentifier(destination),
      move,
      isGood,
      isDeleted,
      updatedAt,
    )
  }

  companion object {
    /** Converts an [DataMove] to a [MoveEntity]. */
    fun convertToEntity(dataMove: DataMove): MoveEntity {
      val isGood = dataMove.isGood
      checkNotNull(isGood) {
        "A StoredMove must have a isGood value to be inserted into the database"
      }
      return MoveEntity(
        dataMove.origin.fenRepresentation,
        dataMove.destination.fenRepresentation,
        dataMove.move,
        isGood,
        dataMove.isDeleted,
        dataMove.updatedAt,
      )
    }
  }
}
