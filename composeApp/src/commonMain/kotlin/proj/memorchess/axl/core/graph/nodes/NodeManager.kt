package proj.memorchess.axl.core.graph.nodes

import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.engine.Game

/**
 * NodeManager is responsible for creating and managing nodes in the chess position graph.
 *
 * @param NodeT The type of node being managed.
 * @property nodeConstructor A function to construct a new node.
 * @property nodeCache The cache used to store and retrieve nodes and their moves.
 */
class NodeManager<NodeT : Node<NodeT>>(
  private val nodeConstructor: (PositionIdentifier, PreviousAndNextMoves, NodeT?, NodeT?) -> NodeT,
  private val nodeCache: NodeCache,
) : KoinComponent {

  /**
   * Creates the root node.
   *
   * @param startPosition The position identifier to start from. If null, the standard starting
   *   position will be used.
   * @return The root node.
   */
  fun createInitialNode(startPosition: PositionIdentifier? = null): NodeT {
    val result =
      if (startPosition == null) {
        val moves = nodeCache.getOrCreate(PositionIdentifier.START_POSITION, 0)
        nodeConstructor(PositionIdentifier.START_POSITION, moves, null, null)
      } else {
        val moves = nodeCache.get(startPosition)
        checkNotNull(moves) { "Position $startPosition not known." }
        nodeConstructor(startPosition, moves, null, null)
      }
    LOGGER.w {
      "Created initial node for position: ${result.position}\n ${result.previousAndNextMoves}"
    }
    return result
  }

  /**
   * Creates a node.
   *
   * @param game The game at the position we want to store.
   * @param previous The previous node in the graph.
   * @param move The move that led to this position.
   * @return The node.
   */
  fun createNode(game: Game, previous: NodeT, move: String): NodeT {
    val previousNodeMoves =
      nodeCache.getOrCreate(previous.position, previous.previousAndNextMoves.depth)
    val dataMove =
      previousNodeMoves.nextMoves.getOrPut(move) {
        DataMove(previous.position, game.position.createIdentifier(), move)
      }
    val newNodeLinkedMoves =
      nodeCache.getOrCreate(
        game.position.createIdentifier(),
        previous.previousAndNextMoves.depth + 1,
      )
    val previouslyStoredPreviousNode = newNodeLinkedMoves.addPreviousMove(dataMove)
    if (previouslyStoredPreviousNode != null && previouslyStoredPreviousNode != dataMove) {
      LOGGER.w { "Overwriting previous move: $previouslyStoredPreviousNode with $dataMove" }
    }
    val newNode =
      nodeConstructor(game.position.createIdentifier(), newNodeLinkedMoves, previous, null)
    previous.addChild(dataMove, newNode)
    return newNode
  }

  fun clearNextMoves(positionIdentifier: PositionIdentifier) {
    nodeCache.clearNextMoves(positionIdentifier)
  }

  suspend fun clearPreviousMove(positionIdentifier: PositionIdentifier, move: DataMove) {
    nodeCache.clearPreviousMove(positionIdentifier, move)
  }

  /** Resets the cache from the database. */
  suspend fun resetCacheFromSource() {
    nodeCache.resetFromSource()
  }

  fun getNextNodeToLearn(day: Int, previousPlayedMove: DataMove?): DataNode? {
    if (previousPlayedMove == null) {
      return nodeCache.getNodeFromDay(day)
    }
    val candidateNodeFromPrevious =
      nodeCache.getNodeToTrainAfterPosition(day, previousPlayedMove.destination)
    if (candidateNodeFromPrevious != null) {
      return candidateNodeFromPrevious
    }
    return nodeCache.getNodeFromDay(day)
  }

  fun getNumberOfNodesToTrain(day: Int): Int {
    return nodeCache.getNumberOfNodesToTrain(day)
  }

  fun cacheNode(node: DataNode) {
    nodeCache.cacheNode(node)
  }

  fun isKnown(position: PositionIdentifier): Boolean {
    return nodeCache.get(position) != null
  }
}

private val LOGGER = Logger.withTag("NodeManager")
