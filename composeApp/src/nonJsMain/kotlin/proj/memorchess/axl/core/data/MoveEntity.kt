package proj.memorchess.axl.core.data

import androidx.room.Entity
import kotlinx.datetime.LocalDate

/**
 * Room entity representing an [StoredMove].
 *
 * @property origin FEN string of the origin position.
 * @property destination FEN string of the destination position.
 * @property move The move string (e.g., "e4").
 * @property isGood Indicates whether the move has to be learned.
 * @property lastTrainedDate The date when this move was last trained.
 * @property nextTrainedDate The date when this move should be trained next.
 */
@Entity(tableName = "MoveEntity", primaryKeys = ["origin", "destination"])
// TODO: add foreign keys to NodeEntity
data class MoveEntity(
  val origin: String,
  val destination: String,
  val move: String,
  val lastTrainedDate: LocalDate,
  val nextTrainedDate: LocalDate,
  val isGood: Boolean,
) {

  /** Converts to an [StoredMove]. */
  fun toStoredMove(): StoredMove {
    return StoredMove(
      PositionKey(origin),
      PositionKey(destination),
      move,
      isGood,
      lastTrainedDate,
      nextTrainedDate,
    )
  }

  companion object {
    /** Converts an [StoredMove] to a [MoveEntity]. */
    fun convertToEntity(storedMove: StoredMove): MoveEntity {
      return MoveEntity(
        storedMove.origin.fenRepresentation,
        storedMove.destination.fenRepresentation,
        storedMove.move,
        storedMove.lastTrainedDate,
        storedMove.nextTrainedDate,
        storedMove.isGood ?: true,
      )
    }
  }
}
