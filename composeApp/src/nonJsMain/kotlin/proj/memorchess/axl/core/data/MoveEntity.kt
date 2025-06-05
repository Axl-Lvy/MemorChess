package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Ignore

@Entity(tableName = "MoveEntity", primaryKeys = ["origin", "destination"])
data class MoveEntity(
  val origin: String,
  val destination: String,
  override val move: String,
  override val isGood: Boolean = true,
) : IStoredMove {
  @Ignore
  override fun getOrigin(): PositionKey {
    return PositionKey(origin)
  }

  @Ignore
  override fun getDestination(): PositionKey {
    return PositionKey(destination)
  }

  companion object {
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
