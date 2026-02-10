package proj.memorchess.axl.core.data.online.database

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.NodeFetched
import proj.memorchess.axl.shared.data.PositionFetched

/**
 * Converts a [NodeFetched] from the server into a [DataNode].
 *
 * The node contains its position and all associated moves. Moves where this position is the
 * destination become previous moves, and moves where this position is the origin become next moves.
 */
internal fun nodeToDataNode(node: NodeFetched): DataNode {
  val fen = node.position.positionIdentifier
  val previousMoves =
    node.moves
      .filter { it.destination.positionIdentifier == fen }
      .map { it.toDataMove() }
  val nextMoves =
    node.moves
      .filter { it.origin.positionIdentifier == fen }
      .map { it.toDataMove() }
  return DataNode(
    positionIdentifier = PositionIdentifier(fen),
    previousAndNextMoves =
      PreviousAndNextMoves(previousMoves, nextMoves, node.position.depth),
    previousAndNextTrainingDate =
      PreviousAndNextDate(node.position.lastTrainingDate, node.position.nextTrainingDate),
    updatedAt = node.position.updatedAt,
    isDeleted = node.position.isDeleted,
  )
}

/**
 * Converts a flat list of [MoveFetched] into a list of [DataNode], grouping by unique position.
 *
 * Each position appearing as origin or destination in the moves list produces a [DataNode]. Moves
 * are assigned as previous or next moves relative to each position.
 */
internal fun movesToDataNodes(
  moves: List<MoveFetched>,
  withDeletedOnes: Boolean = false,
): List<DataNode> {
  val positionMap = mutableMapOf<String, PositionFetched>()
  val previousMovesMap = mutableMapOf<String, MutableList<DataMove>>()
  val nextMovesMap = mutableMapOf<String, MutableList<DataMove>>()

  for (move in moves) {
    val originFen = move.origin.positionIdentifier
    val destFen = move.destination.positionIdentifier
    positionMap.getOrPut(originFen) { move.origin }
    positionMap.getOrPut(destFen) { move.destination }

    val dataMove = move.toDataMove()
    nextMovesMap.getOrPut(originFen) { mutableListOf() }.add(dataMove)
    previousMovesMap.getOrPut(destFen) { mutableListOf() }.add(dataMove)
  }

  return positionMap.values
    .filter { withDeletedOnes || !it.isDeleted }
    .map { position ->
      val fen = position.positionIdentifier
      DataNode(
        positionIdentifier = PositionIdentifier(fen),
        previousAndNextMoves =
          PreviousAndNextMoves(
            previousMovesMap[fen].orEmpty(),
            nextMovesMap[fen].orEmpty(),
            position.depth,
          ),
        previousAndNextTrainingDate =
          PreviousAndNextDate(position.lastTrainingDate, position.nextTrainingDate),
        updatedAt = position.updatedAt,
        isDeleted = position.isDeleted,
      )
    }
}

/**
 * Converts a list of [DataNode] into a list of [MoveFetched] suitable for the server's POST
 * endpoint.
 *
 * Each next move of each node produces one [MoveFetched]. The origin position is the node itself,
 * and the destination is derived from the move's destination.
 */
internal fun dataNodesToMoves(nodes: List<DataNode>): List<MoveFetched> {
  return nodes.flatMap { node ->
    val originPosition = node.toPositionFetched()
    node.previousAndNextMoves.nextMoves.values.map { dataMove ->
      MoveFetched(
        origin = originPosition,
        destination =
          PositionFetched(
            positionIdentifier = dataMove.destination.fenRepresentation,
            depth = 0,
            lastTrainingDate = node.previousAndNextTrainingDate.previousDate,
            nextTrainingDate = node.previousAndNextTrainingDate.nextDate,
            updatedAt = dataMove.updatedAt,
            isDeleted = dataMove.isDeleted,
          ),
        move = dataMove.move,
        isGood = dataMove.isGood ?: true,
        isDeleted = dataMove.isDeleted,
        updatedAt = dataMove.updatedAt,
      )
    }
  }
}

private fun MoveFetched.toDataMove(): DataMove {
  return DataMove(
    origin = PositionIdentifier(origin.positionIdentifier),
    destination = PositionIdentifier(destination.positionIdentifier),
    move = move,
    isGood = isGood,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
  )
}

private fun DataNode.toPositionFetched(): PositionFetched {
  return PositionFetched(
    positionIdentifier = positionIdentifier.fenRepresentation,
    depth = previousAndNextMoves.depth,
    lastTrainingDate = previousAndNextTrainingDate.previousDate,
    nextTrainingDate = previousAndNextTrainingDate.nextDate,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
  )
}
