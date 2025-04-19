package proj.ankichess.axl.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import proj.ankichess.axl.core.intf.data.IStoredPosition

@Entity(tableName = "PositionEntity")
data class PositionEntity(
  @PrimaryKey(autoGenerate = false) override val fenRepresentation: String,
  val availableMoves: String,
) : IStoredPosition {

  override fun getAvailableMoveList(): List<String> = availableMoves.split(",")

  companion object Converter {
    fun convertToEntity(position: IStoredPosition): PositionEntity {
      return PositionEntity(
        fenRepresentation = position.fenRepresentation,
        availableMoves = position.getAvailableMoveList().joinToString(","),
      )
    }
  }
}
