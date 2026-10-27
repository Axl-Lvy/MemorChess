package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import kotlin.time.Instant

/**
 * Row of the derived `NodeRepertoireTrainable` projection: existence means [positionKey] has a live
 * good outgoing edge tagged [repertoireId]. Recomputed locally and never synced.
 */
@Entity(
  tableName = "NodeRepertoireTrainableEntity",
  primaryKeys = ["positionKey", "repertoireId"],
  indices = [Index(value = ["repertoireId", "lastReview"])],
)
data class NodeRepertoireTrainableEntity(
  val positionKey: String,
  val repertoireId: String,
  val lastReview: Instant?,
)
