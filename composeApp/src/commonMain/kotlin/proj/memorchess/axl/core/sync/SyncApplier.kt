package proj.memorchess.axl.core.sync

import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.NodeCache
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.graph.TrainableProjection

/**
 * Applies rows pulled from `/v1/sync` to local storage, after resolving each against its local
 * copy.
 *
 * A remote win is written through the `applyRemote*` path, which queues no outbox entry, and the
 * touched positions are invalidated so the next resolve reads the applied row. The invalidation
 * follows the write, which is what stops a load already in flight from putting the pre sync row
 * back.
 *
 * @param database The persistence backend.
 * @param cache Cache the applied positions are invalidated in.
 * @param trainable Recomputed after a tag apply.
 */
class SyncApplier(
  private val database: DatabaseQueryManager,
  private val cache: NodeCache,
  private val trainable: TrainableProjection,
) {

  /**
   * Applies a node pulled from `/v1/sync`, after resolving it against the local copy via [resolve].
   * Returns which side won. On [ResolutionSource.REMOTE] the row is written through
   * [DatabaseQueryManager.applyRemoteNode] (no outbox entry, per its own doc) and the position is
   * invalidated so the next resolve reloads it. On [ResolutionSource.LOCAL] nothing is written.
   */
  suspend fun applyNode(remote: NodeSyncRow): ResolutionSource {
    val local = database.getPositionIncludingDeleted(PositionKey(remote.positionKey))
    val resolution = resolve(local?.toNodeSyncRow(), remote)
    if (resolution.source == ResolutionSource.REMOTE) {
      val dataNode =
        remote.toDataNode(
          existingMoves = local?.previousAndNextMoves ?: PreviousAndNextMoves(),
          existingDepth = local?.depth ?: 0,
          existingHasGoodOutgoing = local?.hasGoodOutgoing ?: false,
          existingCreatedAt = local?.createdAt ?: remote.updatedAt,
        )
      database.applyRemoteNode(dataNode)
      cache.invalidate(dataNode.positionKey)
    }
    return resolution.source
  }

  /**
   * Applies a move pulled from `/v1/sync`, after resolving it the same way [applyNode] does. On
   * [ResolutionSource.REMOTE] the move is written through [DatabaseQueryManager.applyRemoteMove],
   * the origin's derived [DataNode.hasGoodOutgoing] is refreshed if the write changed it (a good
   * edge appearing or disappearing must not leave the flag stale), and both endpoints are
   * invalidated.
   */
  suspend fun applyMove(remote: EdgeSyncRow): ResolutionSource {
    val originKey = PositionKey(remote.origin)
    val destinationKey = PositionKey(remote.destination)
    val local = localEdgeSyncRow(originKey, remote.move)
    val resolution = resolve(local, remote)
    if (resolution.source == ResolutionSource.REMOTE) {
      database.applyRemoteMove(remote.toDataMove())
      refreshHasGoodOutgoingIfChanged(originKey)
      cache.invalidate(originKey)
      cache.invalidate(destinationKey)
    }
    return resolution.source
  }

  /**
   * Applies a repertoire registry row pulled from `/v1/sync`, after resolving it against the local
   * copy via [resolve]. No edge changes, so unlike [applyTag] there is nothing to recompute in
   * `NodeRepertoireTrainable`.
   */
  suspend fun applyRepertoire(remote: RepertoireSyncRow): ResolutionSource {
    val local = database.getRepertoireIncludingDeleted(remote.id)
    val resolution = resolve(local?.toRepertoireSyncRow(), remote)
    if (resolution.source == ResolutionSource.REMOTE) {
      database.applyRemoteRepertoire(remote.toDataRepertoire())
    }
    return resolution.source
  }

  /**
   * Applies an edge to repertoire tag pulled from `/v1/sync`, after resolving it the same way. On
   * [ResolutionSource.REMOTE] the tag is written through [DatabaseQueryManager.applyRemoteTag] and
   * the origin's `NodeRepertoireTrainable` row set is recomputed, mirroring how [applyMove]
   * refreshes [DataNode.hasGoodOutgoing].
   */
  suspend fun applyTag(remote: EdgeRepertoireTagSyncRow): ResolutionSource {
    val originKey = PositionKey(remote.origin)
    val destinationKey = PositionKey(remote.destination)
    val local = database.getTagIncludingDeleted(originKey, destinationKey, remote.repertoireId)
    val resolution = resolve(local?.toEdgeRepertoireTagSyncRow(), remote)
    if (resolution.source == ResolutionSource.REMOTE) {
      database.applyRemoteTag(remote.toDataEdgeRepertoireTag())
      trainable.recompute(originKey)
    }
    return resolution.source
  }

  /**
   * Hard wipes every position and move, both in the cache and on disk. Leaves the outbox untouched.
   */
  suspend fun eraseAll() {
    database.eraseAll()
    cache.clear()
  }

  /**
   * The local counterpart of a pulled edge, as an [EdgeSyncRow], or `null` when unknown locally.
   */
  private suspend fun localEdgeSyncRow(origin: PositionKey, move: String): EdgeSyncRow? =
    database
      .getPositionIncludingDeleted(origin)
      ?.previousAndNextMoves
      ?.nextMoves
      ?.get(move)
      ?.toEdgeSyncRow()

  /**
   * Re-derives [origin]'s [DataNode.hasGoodOutgoing] from its own move maps and re-persists it,
   * without an outbox entry, only when the value actually changed.
   */
  private suspend fun refreshHasGoodOutgoingIfChanged(origin: PositionKey) {
    val node = database.getPositionIncludingDeleted(origin) ?: return
    val recomputed =
      node.previousAndNextMoves.nextMoves.values.any { it.isGood == true && !it.isDeleted }
    if (recomputed != node.hasGoodOutgoing) {
      database.applyRemoteNode(node.copy(hasGoodOutgoing = recomputed))
    }
  }
}
