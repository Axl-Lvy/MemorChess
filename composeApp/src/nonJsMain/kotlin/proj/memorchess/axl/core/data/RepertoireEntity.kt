package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.date.DateUtil

/** Room entity representing a [DataRepertoire]. */
@Entity(
  tableName = "RepertoireEntity",
  indices = [Index(value = ["isDeleted"]), Index(value = ["updatedAt"])],
)
data class RepertoireEntity(
  @PrimaryKey(autoGenerate = false) val repertoireId: String,
  val name: String,
  val color: String?,
  val isDeleted: Boolean = false,
  val updatedAt: Instant = DateUtil.now(),
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
) {

  fun toDataRepertoire(): DataRepertoire =
    DataRepertoire(
      id = repertoireId,
      name = name,
      color = color?.let { RepertoireColor.valueOf(it) },
      isDeleted = isDeleted,
      updatedAt = updatedAt,
      originDevice = originDevice,
      deviceSeq = deviceSeq,
    )

  companion object {
    fun convertToEntity(repertoire: DataRepertoire): RepertoireEntity =
      RepertoireEntity(
        repertoireId = repertoire.id,
        name = repertoire.name,
        color = repertoire.color?.name,
        isDeleted = repertoire.isDeleted,
        updatedAt = repertoire.updatedAt,
        originDevice = repertoire.originDevice,
        deviceSeq = repertoire.deviceSeq,
      )
  }
}
