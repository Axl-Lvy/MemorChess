package proj.memorchess.axl.core.data

import androidx.room.Entity

/**
 * Room entity representing an [IStoredMove].
 *
 * @property origin FEN string of the origin position.
 * @property destination FEN string of the destination position.
 * @property move The move string (e.g., "e4").
 * @property isGood Indicates whether the move has to be learned.
 */
@Entity(tableName = "MoveEntity", primaryKeys = ["origin", "destination"])
// TODO: add foreign keys to NodeEntity
data class MoveEntity(
  val origin: String,
  val destination: String,
  val move: String,
  val isGood: Boolean = true,
) {

  /** Converts to an [IStoredMove]. */
  fun toStoredMove(): IStoredMove {
    return StoredMove(PositionKey(origin), PositionKey(destination), move, isGood)
  }

  companion object {
    /** Converts an [IStoredMove] to a [MoveEntity]. */
    fun convertToEntity(storedMove: IStoredMove): MoveEntity {
      return MoveEntity(
        storedMove.getOrigin().fenRepresentation,
        storedMove.getDestination().fenRepresentation,
        storedMove.move,
        storedMove.isGood,
      )
    }
  }
}
