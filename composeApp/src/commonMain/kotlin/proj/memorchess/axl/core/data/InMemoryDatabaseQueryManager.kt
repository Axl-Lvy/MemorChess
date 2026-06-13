package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.PreviousAndNextMoves

/**
 * In memory [DatabaseQueryManager] with no persistence at all.
 *
 * It backs throwaway [proj.memorchess.axl.core.graph.TreeStore] instances that exist only for the
 * lifetime of a single screen, such as the read only repertoire viewer which rebuilds an isolated
 * opening graph from a downloaded PGN every time it opens. Nothing here touches Room or IndexedDB,
 * so it works identically on every target and leaves the user's real graph untouched.
 *
 * Behaviour mirrors the platform implementations closely enough for [TreeStore]: hard deletes
 * physically remove rows and any incident move, soft deletes flip the [DataNode.isDeleted] flag.
 *
 * Open so tests can subclass it with prefilled-opening fixtures instead of reimplementing the
 * storage; subclasses read and seed the graph through [nodes].
 */
open class InMemoryDatabaseQueryManager : DatabaseQueryManager {

  /** Backing store, keyed by position. Soft-deleted nodes stay here with their flag set. */
  protected val nodes: MutableMap<PositionKey, DataNode> = mutableMapOf()

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> =
    nodes.values.filter { withDeletedOnes || !it.isDeleted }.toList()

  override suspend fun getPosition(positionKey: PositionKey): DataNode? =
    nodes[positionKey]?.takeIf { !it.isDeleted }

  override suspend fun insertNodes(vararg positions: DataNode) {
    positions.forEach { nodes[it.positionKey] = it }
  }

  override suspend fun deletePosition(position: PositionKey, mode: DeleteMode) {
    val node = nodes[position] ?: return
    when (mode) {
      DeleteMode.HARD -> {
        nodes.remove(position)
        // Drop any move that pointed to or came from the removed position.
        for ((key, other) in nodes.toMap()) {
          val moves = other.previousAndNextMoves
          val previousMoves = moves.previousMoves.values.filter { it.origin != position }
          val nextMoves = moves.nextMoves.values.filter { it.destination != position }
          if (
            previousMoves.size != moves.previousMoves.size || nextMoves.size != moves.nextMoves.size
          ) {
            nodes[key] =
              other.copy(previousAndNextMoves = PreviousAndNextMoves(previousMoves, nextMoves))
          }
        }
      }
      DeleteMode.SOFT -> nodes[position] = node.copy(isDeleted = true)
    }
  }

  override suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode) {
    val node = nodes[origin] ?: return
    val edge = node.previousAndNextMoves.nextMoves[move] ?: return
    val destination = edge.destination
    nodes[origin] =
      node.copy(previousAndNextMoves = node.previousAndNextMoves.withoutNext(move, mode))
    val destinationNode = nodes[destination] ?: return
    nodes[destination] =
      destinationNode.copy(
        previousAndNextMoves = destinationNode.previousAndNextMoves.withoutPrevious(move, mode)
      )
  }

  override suspend fun eraseAll() {
    nodes.clear()
  }

  override suspend fun getLastUpdate(): Instant? {
    val nodeMax = nodes.values.maxOfOrNull { it.updatedAt }
    val moveMax =
      nodes.values
        .flatMap {
          (it.previousAndNextMoves.previousMoves + it.previousAndNextMoves.nextMoves).values
        }
        .maxOfOrNull { it.updatedAt }
    return when {
      nodeMax == null -> moveMax
      moveMax == null -> nodeMax
      else -> maxOf(nodeMax, moveMax)
    }
  }

  private fun PreviousAndNextMoves.withoutNext(
    move: String,
    mode: DeleteMode,
  ): PreviousAndNextMoves =
    PreviousAndNextMoves(previousMoves.values, removeOrFlag(nextMoves, move, mode))

  private fun PreviousAndNextMoves.withoutPrevious(
    move: String,
    mode: DeleteMode,
  ): PreviousAndNextMoves =
    PreviousAndNextMoves(removeOrFlag(previousMoves, move, mode), nextMoves.values)

  private fun removeOrFlag(
    moves: Map<String, DataMove>,
    move: String,
    mode: DeleteMode,
  ): List<DataMove> =
    moves.values.mapNotNull {
      if (it.move != move) it
      else
        when (mode) {
          DeleteMode.HARD -> null
          DeleteMode.SOFT -> it.copy(isDeleted = true)
        }
    }
}
