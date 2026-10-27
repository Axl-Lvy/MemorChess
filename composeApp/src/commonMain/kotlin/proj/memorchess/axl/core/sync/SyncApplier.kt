package proj.memorchess.axl.core.sync

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.NodeCache
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.graph.TrainableProjection
import proj.memorchess.axl.core.graph.effectiveMarking
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Applies rows pulled from `/v1/sync` to local storage, after resolving each against its local
 * copy.
 *
 * A remote win is written through the `applyRemote*` path, which queues no outbox entry, and the
 * touched positions are invalidated so the next resolve reads the applied row. The invalidation
 * follows the write, which is what stops a load already in flight from putting the pre sync row
 * back.
 *
 * A pulled manual hold or tag changes who holds a move, so both re-derive the move's effective
 * marking and existence from its holders (see [effectiveMarking]). A tag can arrive before its
 * endpoint positions (a later review re-pushes a position at a later revision): it is stored
 * without creating its move, and the position's arrival re-derives the moves its tags hold. A move
 * a holder keeps revives a deleted endpoint position with a fresh review state, and an endpoint
 * other than the start left with no live move once a move is deleted is deleted too, so a position
 * revived ahead of the tombstones of an uninstall does not stay behind.
 *
 * @param database The persistence backend.
 * @param cache Cache the applied positions are invalidated in.
 * @param trainable Recomputed after every holder change.
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
      for (tag in database.getTagsTouching(dataNode.positionKey)) {
        if (tag.isGood != null) recomputeHolders(tag.origin, tag.destination, tag.move)
      }
      // A tombstone does not delete a position a live move still reaches: its holders revive it.
      if (remote.isDeleted && local != null) {
        val moves = local.previousAndNextMoves
        for (move in moves.previousMoves.values + moves.nextMoves.values) {
          if (!move.isDeleted) recomputeHolders(move.origin, move.destination, move.move)
        }
      }
    }
    return resolution.source
  }

  /**
   * Applies a manual hold pulled from `/v1/sync`, after resolving it the same way [applyNode] does;
   * a move this device never held manually counts as absent. On [ResolutionSource.REMOTE] the hold
   * is written through [DatabaseQueryManager.applyRemoteMove] and the move's holders are
   * re-derived.
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
   * the move's holders are re-derived, creating the move from the tag when it is the move's first
   * holder on this device.
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
   * Re-derives the effective marking and existence of the move from [origin] to [destination] from
   * its manual hold and its live tags, without an outbox entry, keeping the manual hold's stamps. A
   * move with no stored row is created as [move] when it has a holder and both endpoints exist.
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
    if (stored != null && !stored.isDeleted && updated?.isDeleted == true) {
      pruneIfOrphaned(origin)
      pruneIfOrphaned(destination)
    }
    refreshHasGoodOutgoingIfChanged(origin)
    cache.invalidate(origin)
    cache.invalidate(destination)
    trainable.recompute(origin)
  }

  /**
   * Deletes [position], without an outbox entry, when it is live, is not the start, and has no live
   * move left.
   */
  private suspend fun pruneIfOrphaned(position: PositionKey) {
    if (position == PositionKey.START_POSITION) return
    val node = database.getPositionIncludingDeleted(position) ?: return
    if (node.isDeleted) return
    val moves = node.previousAndNextMoves
    if ((moves.previousMoves.values + moves.nextMoves.values).any { !it.isDeleted }) return
    database.applyRemoteNode(node.copy(isDeleted = true))
    trainable.clear(position)
    cache.invalidate(position)
  }

  /**
   * Brings back the deleted position [position] with a fresh review state, without an outbox entry.
   */
  private suspend fun reviveIfDeleted(position: PositionKey) {
    val node = database.getPositionIncludingDeleted(position) ?: return
    if (!node.isDeleted) return
    database.applyRemoteNode(node.copy(isDeleted = false, cardState = CardStateFactory.new()))
  }

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
