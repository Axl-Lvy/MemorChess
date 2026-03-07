package proj.memorchess.axl.core.graph.nodes

import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine

/**
 * NodeManager is responsible for creating and managing nodes in the chess position graph.
 *
 * @param NodeT The type of node being managed.
 * @property nodeConstructor A function to construct a new node.
 * @property nodeCache The cache used to store and retrieve nodes and their moves.
 */
class NodeManager<NodeT : Node<NodeT>>(
  private val nodeConstructor: (PositionKey, PreviousAndNextMoves, NodeT?, NodeT?) -> NodeT,
  private val nodeCache: NodeCache,
) : KoinComponent {

  /**
   * Creates the root node.
   *
   * @param startPosition The position identifier to start from. If null, the standard starting
   *   position will be used.
   * @return The root node.
   */
  fun createInitialNode(startPosition: PositionKey? = null): NodeT {
    val result =
      if (startPosition == null) {
        val moves = nodeCache.getOrCreate(PositionKey.START_POSITION, 0)
        nodeConstructor(PositionKey.START_POSITION, moves, null, null)
      } else {
        val moves = nodeCache.get(startPosition)
        checkNotNull(moves) { "Position $startPosition not known." }
        nodeConstructor(startPosition, moves, null, null)
      }
    return result
  }

  /**
   * Creates a node.
   *
   * @param engine The game engine at the position we want to store.
   * @param previous The previous node in the graph.
   * @param move The move that led to this position.
   * @return The node.
   */
  fun createNode(engine: GameEngine, previous: NodeT, move: String): NodeT {
    val previousNodeMoves =
      nodeCache.getOrCreate(previous.position, previous.previousAndNextMoves.depth)
    val destination = engine.toPositionKey()
    val dataMove =
      previousNodeMoves.nextMoves.getOrPut(move) { DataMove(previous.position, destination, move) }
    val newNodeLinkedMoves =
      nodeCache.getOrCreate(destination, previous.previousAndNextMoves.depth + 1)
    val previouslyStoredPreviousNode = newNodeLinkedMoves.addPreviousMove(dataMove)
    if (previouslyStoredPreviousNode != null && previouslyStoredPreviousNode != dataMove) {
      LOGGER.w { "Overwriting previous move: $previouslyStoredPreviousNode with $dataMove" }
    }
    val newNode = nodeConstructor(destination, newNodeLinkedMoves, previous, null)
    previous.addChild(dataMove, newNode)
    return newNode
  }

  fun clearNextMoves(positionKey: PositionKey) {
    nodeCache.clearNextMoves(positionKey)
  }

  suspend fun clearPreviousMove(positionKey: PositionKey, move: DataMove) {
    nodeCache.clearPreviousMove(positionKey, move)
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

  fun isKnown(position: PositionKey): Boolean {
    return nodeCache.get(position) != null
  }
}

private val LOGGER = Logger.withTag("NodeManager")
