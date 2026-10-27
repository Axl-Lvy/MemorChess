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
 * The wire shape of [DataNode]'s scheduling fields. `depth`, `hasGoodOutgoing`, `createdAt` and
 * [DataNode.previousAndNextMoves] never travel.
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
 * Rebuilds a [DataNode] from a pulled [NodeSyncRow] and the caller-supplied local-only fields. An
 * unknown [NodeSyncRow.phase] falls back to [CardPhase.NEW].
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
 * The wire shape of this move's manual hold, or `null` when none was ever written. A removed manual
 * hold maps to a tombstone.
 */
fun DataMove.toEdgeSyncRow(): EdgeSyncRow? {
  if (originDevice.isEmpty() && deviceSeq == 0L) return null
  return EdgeSyncRow(
    origin = origin.value,
    destination = destination.value,
    move = move,
    isGood = ownIsGood ?: false,
    isDeleted = isDeleted || ownIsGood == null,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )
}

/**
 * Rebuilds a [DataMove] from a pulled [EdgeSyncRow]. [DataMove.isGood] holds the wire value until
 * the caller derives the effective marking.
 */
fun EdgeSyncRow.toDataMove(): DataMove =
  DataMove(
    origin = PositionKey(origin),
    destination = PositionKey(destination),
    move = move,
    isGood = isGood,
    ownIsGood = if (isDeleted) null else isGood,
    isDeleted = isDeleted,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

/** The wire shape of a [DataRepertoire]. */
fun DataRepertoire.toRepertoireSyncRow(): RepertoireSyncRow =
  RepertoireSyncRow(
    id = id,
    name = name,
    color = color?.name,
    installedVersion = installedVersion,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

/** Rebuilds a [DataRepertoire] from a pulled [RepertoireSyncRow]. */
fun RepertoireSyncRow.toDataRepertoire(): DataRepertoire =
  DataRepertoire(
    id = id,
    name = name,
    color = color?.let { RepertoireColor.valueOf(it) },
    installedVersion = installedVersion,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

/** The wire shape of a [DataEdgeRepertoireTag]. */
fun DataEdgeRepertoireTag.toEdgeRepertoireTagSyncRow(): EdgeRepertoireTagSyncRow =
  EdgeRepertoireTagSyncRow(
    origin = origin.value,
    destination = destination.value,
    repertoireId = repertoireId,
    move = move,
    isGood = isGood,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

/** Rebuilds a [DataEdgeRepertoireTag] from a pulled [EdgeRepertoireTagSyncRow]. */
fun EdgeRepertoireTagSyncRow.toDataEdgeRepertoireTag(): DataEdgeRepertoireTag =
  DataEdgeRepertoireTag(
    origin = PositionKey(origin),
    destination = PositionKey(destination),
    repertoireId = repertoireId,
    move = move,
    isGood = isGood,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )
