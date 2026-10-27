package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey

/**
 * Projects a position's live, good, tagged outgoing edges into its `NodeRepertoireTrainable` rows.
 * Reads tags from [DatabaseQueryManager.getTags], not [RepertoireTagStore], to avoid a cycle.
 */
class TrainableProjection(
  private val database: DatabaseQueryManager,
  private val cache: NodeCache,
) {

  /**
   * Rewrites [origin]'s rows, one per repertoire tagging a live, good outgoing edge, stamped with
   * [origin]'s last review. A no-op when [origin] cannot be resolved.
   */
  suspend fun recompute(origin: PositionKey) {
    val resolved = cache.resolve(origin).node ?: return
    val repertoireIds = mutableSetOf<String>()
    for (edge in resolved.outgoing.values) {
      if (edge.isGood != true || edge.isDeleted) continue
      repertoireIds += database.getTags(edge.from, edge.to).map { it.repertoireId }
    }
    database.replaceTrainableRepertoires(origin, repertoireIds, resolved.cardState.lastReview)
  }

  /** Empties [positionKey]'s row set. */
  suspend fun clear(positionKey: PositionKey) {
    database.replaceTrainableRepertoires(positionKey, emptySet(), null)
  }
}
