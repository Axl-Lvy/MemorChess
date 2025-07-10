package proj.memorchess.axl.core.data

import androidx.room.Entity

/**
 * Room entity representing an [StoredMove].
 *
 * @property origin FEN string of the origin position.
 * @property destination FEN string of the destination position.
 * @property move The move string (e.g., "e4").
 * @property isGood Indicates whether the move has to be learned.
 */
@Entity(tableName = "MoveEntity", primaryKeys = ["origin", "destination"])
data class MoveEntity(
  val origin: String,
  val destination: String,
  val move: String,
  val isGood: Boolean,
) {

  /** Converts to an [StoredMove]. */
  fun toStoredMove(): StoredMove {
    return StoredMove(PositionIdentifier(origin), PositionIdentifier(destination), move, isGood)
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
