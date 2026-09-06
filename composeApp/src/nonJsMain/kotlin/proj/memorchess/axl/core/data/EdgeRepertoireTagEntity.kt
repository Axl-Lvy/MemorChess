package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

/** Room entity representing a [DataEdgeRepertoireTag]. */
@Entity(
  tableName = "EdgeRepertoireTagEntity",
  primaryKeys = ["origin", "destination", "repertoireId"],
  indices =
    [
      Index(value = ["origin", "destination"]),
      Index(value = ["isDeleted"]),
      Index(value = ["updatedAt"]),
    ],
)
data class EdgeRepertoireTagEntity(
  val origin: String,
  val destination: String,
  val repertoireId: String,
  val isDeleted: Boolean = false,
  val updatedAt: Instant = DateUtil.now(),
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
) {

  fun toDataEdgeRepertoireTag(): DataEdgeRepertoireTag =
    DataEdgeRepertoireTag(
      origin = PositionKey(origin),
      destination = PositionKey(destination),
      repertoireId = repertoireId,
      isDeleted = isDeleted,
      updatedAt = updatedAt,
      originDevice = originDevice,
      deviceSeq = deviceSeq,
    )

  companion object {
    fun convertToEntity(tag: DataEdgeRepertoireTag): EdgeRepertoireTagEntity =
      EdgeRepertoireTagEntity(
        origin = tag.origin.value,
        destination = tag.destination.value,
        repertoireId = tag.repertoireId,
        isDeleted = tag.isDeleted,
        updatedAt = tag.updatedAt,
        originDevice = tag.originDevice,
        deviceSeq = tag.deviceSeq,
      )
  }
}
