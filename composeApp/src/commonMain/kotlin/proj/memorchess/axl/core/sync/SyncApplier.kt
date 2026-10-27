package proj.memorchess.axl.core.sync

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.NodeCache
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.graph.TrainableProjection
import proj.memorchess.axl.core.graph.effectiveMarking
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Applies rows pulled from `/v1/sync` to local storage when they win resolution against the local
 * copy, without queuing outbox entries.
 */
class SyncApplier(
  private val database: DatabaseQueryManager,
  private val cache: NodeCache,
  private val trainable: TrainableProjection,
) {

  /** Applies a pulled node if it wins resolution, and returns which side won. */
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
      // After the write, so a load already in flight cannot put the pre sync row back.
      cache.invalidate(dataNode.positionKey)
      // A tag can arrive before its endpoint positions, so their arrival re-derives its move.
      for (tag in database.getTagsTouching(dataNode.positionKey)) {
        if (tag.isGood != null) recomputeHolders(tag.origin, tag.destination, tag.move)
      }
    }
    return resolution.source
  }

  /**
   * Applies a pulled manual hold the same way as [applyNode]; a move this device never held
   * manually counts as absent.
   */
  suspend fun applyMove(remote: EdgeSyncRow): ResolutionSource {
    val originKey = PositionKey(remote.origin)
    val destinationKey = PositionKey(remote.destination)
    val stored = storedMove(originKey, destinationKey)
    val resolution = resolve(stored?.toEdgeSyncRow(), remote)
    if (resolution.source == ResolutionSource.REMOTE) {
      val pulled = remote.toDataMove()
      database.applyRemoteMove(
        stored?.copy(
          ownIsGood = pulled.ownIsGood,
          isDeleted = pulled.isDeleted,
          updatedAt = pulled.updatedAt,
          originDevice = pulled.originDevice,
          deviceSeq = pulled.deviceSeq,
        ) ?: pulled
      )
      recomputeHolders(originKey, destinationKey, remote.move)
    }
    return resolution.source
  }

  /** Applies a pulled repertoire registry row the same way as [applyNode]. */
  suspend fun applyRepertoire(remote: RepertoireSyncRow): ResolutionSource {
    val local = database.getRepertoireIncludingDeleted(remote.id)
    val resolution = resolve(local?.toRepertoireSyncRow(), remote)
    if (resolution.source == ResolutionSource.REMOTE) {
      database.applyRemoteRepertoire(remote.toDataRepertoire())
    }
    return resolution.source
  }

  /**
   * Applies a pulled edge to repertoire tag the same way as [applyNode], creating its move when the
   * tag is the move's first holder on this device.
   */
  suspend fun applyTag(remote: EdgeRepertoireTagSyncRow): ResolutionSource {
    val originKey = PositionKey(remote.origin)
    val destinationKey = PositionKey(remote.destination)
    val local = database.getTagIncludingDeleted(originKey, destinationKey, remote.repertoireId)
    val resolution = resolve(local?.toEdgeRepertoireTagSyncRow(), remote)
    if (resolution.source == ResolutionSource.REMOTE) {
      database.applyRemoteTag(remote.toDataEdgeRepertoireTag())
      recomputeHolders(originKey, destinationKey, remote.move)
    }
    return resolution.source
  }

  /** Hard wipes every synced row, in the cache and on disk, and the outbox with them. */
  suspend fun eraseAll() {
    database.eraseAll()
    cache.clear()
  }

  /** The stored move from [origin] to [destination], live or deleted, or `null` when unknown. */
  private suspend fun storedMove(origin: PositionKey, destination: PositionKey): DataMove? =
    database
      .getPositionIncludingDeleted(origin)
      ?.previousAndNextMoves
      ?.nextMoves
      ?.values
      ?.firstOrNull { it.destination == destination }

  /**
   * Re-derives the move's effective marking and existence from its manual hold and live tags,
   * keeping the hold's stamps. A missing move is created only when both endpoints exist.
   */
  private suspend fun recomputeHolders(
    origin: PositionKey,
    destination: PositionKey,
    move: String,
  ) {
    val stored = storedMove(origin, destination)
    val effective =
      effectiveMarking(
        stored?.ownIsGood,
        database.getTags(origin, destination).mapNotNull { it.isGood },
      )
    val updated =
      when {
        stored != null && effective == null -> stored.copy(isDeleted = true)
        stored != null -> stored.copy(isGood = effective, isDeleted = false)
        effective == null -> null
        database.getPositionIncludingDeleted(destination) == null -> null
        database.getPositionIncludingDeleted(origin) == null -> null
        else ->
          DataMove(
            origin = origin,
            destination = destination,
            move = move,
            isGood = effective,
            createdAt = DateUtil.now(),
            updatedAt = DateUtil.now(),
          )
      }
    if (updated != null && !updated.isDeleted) {
      reviveIfDeleted(origin)
      reviveIfDeleted(destination)
    }
    if (updated != null && updated != stored) database.applyRemoteMove(updated)
    refreshHasGoodOutgoingIfChanged(origin)
    cache.invalidate(origin)
    cache.invalidate(destination)
    trainable.recompute(origin)
  }

  /** Restores [position] with a fresh review state if it is deleted. */
  private suspend fun reviveIfDeleted(position: PositionKey) {
    val node = database.getPositionIncludingDeleted(position) ?: return
    if (!node.isDeleted) return
    database.applyRemoteNode(node.copy(isDeleted = false, cardState = CardStateFactory.new()))
  }

  private suspend fun refreshHasGoodOutgoingIfChanged(origin: PositionKey) {
    val node = database.getPositionIncludingDeleted(origin) ?: return
    val recomputed =
      node.previousAndNextMoves.nextMoves.values.any { it.isGood == true && !it.isDeleted }
    if (recomputed != node.hasGoodOutgoing) {
      database.applyRemoteNode(node.copy(hasGoodOutgoing = recomputed))
    }
  }
}
