package proj.memorchess.axl.core.sync

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataEdgeRepertoireTag
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DataRepertoire
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

/**
 * The wire shape of [DataNode]'s scheduling fields, per the parent spec section 5.1: `depth`,
 * `hasGoodOutgoing`, `createdAt` and [DataNode.previousAndNextMoves] never travel.
 */
fun DataNode.toNodeSyncRow(): NodeSyncRow =
  NodeSyncRow(
    positionKey = positionKey.value,
    dueDate = cardState.dueDate,
    lastReview = cardState.lastReview,
    firstReview = cardState.firstReview,
    stability = cardState.stability,
    difficulty = cardState.difficulty,
    reps = cardState.reps,
    lapses = cardState.lapses,
    phase = cardState.phase.name,
    step = cardState.step,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

/**
 * Rebuilds a [DataNode] from a pulled [NodeSyncRow], keeping the caller-supplied local-only fields
 * untouched since the wire row carries none of them. An unrecognized [NodeSyncRow.phase] (a future
 * server sending a phase this client predates) falls back to [CardPhase.NEW] rather than throwing,
 * the same tolerance [proj.memorchess.axl.core.data.NodeWithMoves] already applies for Room reads.
 */
fun NodeSyncRow.toDataNode(
  existingMoves: PreviousAndNextMoves,
  existingDepth: Int,
  existingHasGoodOutgoing: Boolean,
  existingCreatedAt: Instant,
): DataNode =
  DataNode(
    positionKey = PositionKey(positionKey),
    previousAndNextMoves = existingMoves,
    cardState =
      CardState(
        dueDate = dueDate,
        lastReview = lastReview,
        firstReview = firstReview,
        stability = stability,
        difficulty = difficulty,
        reps = reps,
        lapses = lapses,
        phase = runCatching { CardPhase.valueOf(phase) }.getOrDefault(CardPhase.NEW),
        step = step,
      ),
    depth = existingDepth,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    hasGoodOutgoing = existingHasGoodOutgoing,
    createdAt = existingCreatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

/**
 * The wire shape of a [DataMove]. [DataMove.isGood] is non-nullable on the wire (an
 * exploration-only, unclassified move is never persisted, so it is never pushed either).
 */
fun DataMove.toEdgeSyncRow(): EdgeSyncRow =
  EdgeSyncRow(
    origin = origin.value,
    destination = destination.value,
    move = move,
    isGood = isGood ?: false,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

/**
 * Rebuilds a [DataMove] from a pulled [EdgeSyncRow]. [DataMove.createdAt] is not part of the wire
 * shape (see [DataMove.createdAt]'s own doc: excluded from equality, "the same move regardless of
 * when it was recorded") and defaults to [EdgeSyncRow.updatedAt] here, the same as a freshly
 * created local move whose real creation time predates this device's knowledge of it.
 */
fun EdgeSyncRow.toDataMove(): DataMove =
  DataMove(
    origin = PositionKey(origin),
    destination = PositionKey(destination),
    move = move,
    isGood = isGood,
    isDeleted = isDeleted,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

/** The wire shape of a [DataRepertoire]. */
fun DataRepertoire.toRepertoireSyncRow(): RepertoireSyncRow =
  RepertoireSyncRow(id, name, color?.name, isDeleted, updatedAt, originDevice, deviceSeq)

/** Rebuilds a [DataRepertoire] from a pulled [RepertoireSyncRow]. */
fun RepertoireSyncRow.toDataRepertoire(): DataRepertoire =
  DataRepertoire(
    id,
    name,
    color?.let { RepertoireColor.valueOf(it) },
    isDeleted,
    updatedAt,
    originDevice,
    deviceSeq,
  )

/** The wire shape of a [DataEdgeRepertoireTag]. */
fun DataEdgeRepertoireTag.toEdgeRepertoireTagSyncRow(): EdgeRepertoireTagSyncRow =
  EdgeRepertoireTagSyncRow(
    origin.value,
    destination.value,
    repertoireId,
    isDeleted,
    updatedAt,
    originDevice,
    deviceSeq,
  )

/** Rebuilds a [DataEdgeRepertoireTag] from a pulled [EdgeRepertoireTagSyncRow]. */
fun EdgeRepertoireTagSyncRow.toDataEdgeRepertoireTag(): DataEdgeRepertoireTag =
  DataEdgeRepertoireTag(
    PositionKey(origin),
    PositionKey(destination),
    repertoireId,
    isDeleted,
    updatedAt,
    originDevice,
    deviceSeq,
  )
