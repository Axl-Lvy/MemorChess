package proj.ankichess.axl.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PositionEntity(
  @PrimaryKey(autoGenerate = false) val fenRepresentation: String,
  val availableMoves: String,
)
