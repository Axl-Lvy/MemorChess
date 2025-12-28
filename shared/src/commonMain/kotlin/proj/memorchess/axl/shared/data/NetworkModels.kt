@file:OptIn(ExperimentalTime::class)

package proj.memorchess.axl.shared.data

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val IS_DELETED_FIELD = "is_deleted"
internal const val UPDATED_AT_FIELD = "updated_at"
internal const val DEPTH_FIELD = "depth"
internal const val NEXT_TRAINING_DATE_FIELD = "next_training_date"
internal const val LAST_TRAINING_DATE_FIELD = "last_training_date"

@Serializable
data class PositionFetched(
  val positionIdentifier: String,
  @SerialName(DEPTH_FIELD) val depth: Int,
  @SerialName(LAST_TRAINING_DATE_FIELD) val lastTrainingDate: LocalDate,
  @SerialName(NEXT_TRAINING_DATE_FIELD) val nextTrainingDate: LocalDate,
  @SerialName(UPDATED_AT_FIELD) val updatedAt: Instant,
  @SerialName(IS_DELETED_FIELD) val isDeleted: Boolean,
)

@Serializable
data class MoveFetched(
  val origin: PositionFetched,
  val destination: PositionFetched,
  val move: String,
  var isGood: Boolean?,
  val isDeleted: Boolean,
  val updatedAt: Instant,
)
