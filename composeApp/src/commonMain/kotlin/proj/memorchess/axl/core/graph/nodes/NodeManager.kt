package proj.memorchess.axl.core.graph.nodes

import com.diamondedge.logging.logging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game

/** Node factory singleton. */
class NodeManager : KoinComponent {

  /** Reference to the NodeCache singleton. */
  private val nodeCache: NodeCache by inject()

  /**
   * Creates the root node.
   *
   * @return The root node.
   */
  fun createRootNode(): Node {
    val position = PositionIdentifier.START_POSITION
    val rootNodeMoves = nodeCache.getOrCreate(position, 0)
    check(rootNodeMoves.previousMoves.isEmpty()) {
      "Root node should not have previous moves, but found: ${rootNodeMoves.previousMoves}"
    }
    val rootNode = Node(position, rootNodeMoves)
    return rootNode
  }

  /**
   * Creates a node.
   *
   * @param game The game at the position we want to store.
   * @param previous The previous node in the graph.
   * @param move The move that led to this position.
   * @return The node.
   */
  fun createNode(game: Game, previous: Node, move: String): Node {
    val previousNodeMoves =
      nodeCache.getOrCreate(previous.position, previous.previousAndNextMoves.depth)
    val storedMove =
      previousNodeMoves.nextMoves.getOrPut(move) {
        StoredMove(previous.position, game.position.createIdentifier(), move)
      }
    val newNodeLinkedMoves =
      nodeCache.getOrCreate(
        game.position.createIdentifier(),
        previous.previousAndNextMoves.depth + 1,
      )
    val previouslyStoredPreviousNode = newNodeLinkedMoves.addPreviousMove(storedMove)
    if (previouslyStoredPreviousNode != null && previouslyStoredPreviousNode != storedMove) {
      LOGGER.w { "Overwriting previous move: $previouslyStoredPreviousNode with $storedMove" }
    }
    val newNode =
      Node(
        game.position.createIdentifier(),
        previous = previous,
        previousAndNextMoves = newNodeLinkedMoves,
      )
    previous.addChild(storedMove, newNode)
    return newNode
  }

  fun clearNextMoves(positionIdentifier: PositionIdentifier) {
    nodeCache.clearNextMoves(positionIdentifier)
  }

  suspend fun clearPreviousMove(positionIdentifier: PositionIdentifier, move: StoredMove) {
    nodeCache.clearPreviousMove(positionIdentifier, move)
  }

  /** Resets the cache from the database. */
  suspend fun resetCacheFromDataBase() {
    nodeCache.resetGraphFromDatabase()
  }

  /** Resets the cache from the database. */
  suspend fun resetCacheFromDataBase(db: DatabaseQueryManager) {
    nodeCache.resetGraphFromDatabase(db)
  }

  fun getNextNodeToLearn(day: Int, previousPlayedMove: StoredMove?): StoredNode? {
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

  fun cacheNode(node: StoredNode) {
    nodeCache.cacheNode(node)
  }
}

private val LOGGER = logging()
