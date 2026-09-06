package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import kotlin.time.Instant

/**
 * Room entity backing the `NodeRepertoireTrainable` derived projection: existence means
 * [positionKey] has a live good outgoing edge tagged [repertoireId]. Owned entirely by
 * [proj.memorchess.axl.core.graph.TreeStore], recomputed the same way [NodeEntity.hasGoodOutgoing]
 * is, and never synced.
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
