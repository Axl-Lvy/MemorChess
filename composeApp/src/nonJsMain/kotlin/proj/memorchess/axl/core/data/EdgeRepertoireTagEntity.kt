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
      Index(value = ["destination"]),
      Index(value = ["isDeleted"]),
      Index(value = ["updatedAt"]),
    ],
)
data class EdgeRepertoireTagEntity(
  val origin: ByteArray,
  val destination: ByteArray,
  val repertoireId: String,
  val move: String,
  val isGood: Boolean? = null,
  val isDeleted: Boolean = false,
  val updatedAt: Instant = DateUtil.now(),
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
) {

  fun toDataEdgeRepertoireTag(): DataEdgeRepertoireTag =
    DataEdgeRepertoireTag(
      origin = PositionKeyCodec.decode(origin),
      destination = PositionKeyCodec.decode(destination),
      repertoireId = repertoireId,
      move = move,
      isGood = isGood,
      isDeleted = isDeleted,
      updatedAt = updatedAt,
      originDevice = originDevice,
      deviceSeq = deviceSeq,
    )

  companion object {
    fun convertToEntity(tag: DataEdgeRepertoireTag): EdgeRepertoireTagEntity =
      EdgeRepertoireTagEntity(
        origin = PositionKeyCodec.encode(tag.origin),
        destination = PositionKeyCodec.encode(tag.destination),
        repertoireId = tag.repertoireId,
        move = tag.move,
        isGood = tag.isGood,
        isDeleted = tag.isDeleted,
        updatedAt = tag.updatedAt,
        originDevice = tag.originDevice,
        deviceSeq = tag.deviceSeq,
      )
  }
}
