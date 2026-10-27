package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

/**
 * Room entity representing a [DataMove].
 *
 * @property move The move in SAN.
 * @property isGood Effective marking: whether the move has to be learned.
 * @property ownIsGood The user's own marking, or `null` without a manual hold.
 */
@Entity(
  tableName = "MoveEntity",
  primaryKeys = ["origin", "destination"],
  indices =
    [
      Index(value = ["isGood"]),
      Index(value = ["origin"]),
      Index(value = ["destination"]),
      Index(value = ["isDeleted"]),
      Index(value = ["updatedAt"]),
    ],
)
data class MoveEntity(
  val origin: String,
  val destination: String,
  val move: String,
  val isGood: Boolean,
  val ownIsGood: Boolean? = null,
  val isDeleted: Boolean = false,

  /** When the move was first added. */
  val createdAt: Instant = DateUtil.now(),
  val updatedAt: Instant = DateUtil.now(),

  /** Device that wrote this version. */
  val originDevice: String = "",

  /** That device's write counter at the time. */
  val deviceSeq: Long = 0L,
) {

  /** Converts to a [DataMove]. */
  fun toStoredMove(): DataMove {
    return DataMove(
      PositionKey(origin),
      PositionKey(destination),
      move,
      isGood,
      ownIsGood,
      isDeleted,
      createdAt,
      updatedAt,
      originDevice,
      deviceSeq,
    )
  }

  companion object {
    /** Converts a [DataMove] to a [MoveEntity]. */
    fun convertToEntity(dataMove: DataMove): MoveEntity {
      val isGood = dataMove.isGood
      checkNotNull(isGood) {
        "A StoredMove must have a isGood value to be inserted into the database"
      }
      return MoveEntity(
        dataMove.origin.value,
        dataMove.destination.value,
        dataMove.move,
        isGood,
        dataMove.ownIsGood,
        dataMove.isDeleted,
        dataMove.createdAt,
        dataMove.updatedAt,
        dataMove.originDevice,
        dataMove.deviceSeq,
      )
    }
  }
}
