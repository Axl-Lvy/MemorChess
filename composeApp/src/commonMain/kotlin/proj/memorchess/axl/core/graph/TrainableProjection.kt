package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey

/**
 * Projection of a position's live, good, tagged outgoing edges into its `NodeRepertoireTrainable`
 * row set.
 *
 * Reads its tags straight from [DatabaseQueryManager.getTags] rather than through
 * [RepertoireTagStore], whose own tag write recomputes this projection. Depending the other way
 * would close a cycle between the two.
 *
 * @param database Persistence backend.
 * @param cache Cache the origin is resolved through.
 */
class TrainableProjection(
  private val database: DatabaseQueryManager,
  private val cache: NodeCache,
) {

  /**
   * Rewrites [origin]'s entire row set: one row per repertoire with at least one live, good outgoing
   * edge tagged with it, each stamped with [origin]'s current card state review date. A no op when
   * [origin] cannot be resolved.
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

  /** Empties [positionKey]'s row set, for a position that is being deleted. */
  suspend fun clear(positionKey: PositionKey) {
    database.replaceTrainableRepertoires(positionKey, emptySet(), null)
  }
}
