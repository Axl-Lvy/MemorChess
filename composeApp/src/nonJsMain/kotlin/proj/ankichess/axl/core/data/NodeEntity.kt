package proj.ankichess.axl.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import proj.ankichess.axl.core.impl.data.PositionKey
import proj.ankichess.axl.core.intf.data.IStoredNode

@Entity(tableName = "NodeEntity")
data class NodeEntity(
  @PrimaryKey(autoGenerate = false) val fenRepresentation: String,
  val availableMoves: String,
) : IStoredNode {
  override val positionKey: PositionKey
    get() = PositionKey(fenRepresentation)

  override fun getAvailableMoveList(): List<String> =
    availableMoves.split(",").filter { it.isNotBlank() }

  companion object Converter {
    fun convertToEntity(position: IStoredNode): NodeEntity {
      return NodeEntity(
        fenRepresentation = position.positionKey.fenRepresentation,
        availableMoves = position.getAvailableMoveList().joinToString(","),
      )
    }
  }
}
