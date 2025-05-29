package proj.ankichess.axl.core.impl.graph.nodes

import com.diamondedge.logging.logging
import proj.ankichess.axl.core.impl.data.PositionKey
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.intf.data.DatabaseHolder
import proj.ankichess.axl.core.intf.data.IStoredNode

/** Node factory singleton. */
object NodeFactory {

  private var databaseRetrieved = false

  /**
   * Cache to prevent creating a node twice.
   *
   * TODO: handle many index
   */
  private val movesCache = mutableMapOf<PositionKey, PreviousAndNextMoves>()

  /**
   * Creates the root node.
   *
   * @return The root node.
   */
  fun createRootNode(): Node {
    val position = Game().position.toImmutablePosition()
    val rootNode = Node(position)
    val newNodeMoves = movesCache.getOrPut(position) { PreviousAndNextMoves() }
    rootNode.linkedMoves.nextMoves.addAll(newNodeMoves.nextMoves)
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
    val previousNodeMoves = movesCache.getOrPut(previous.position) { PreviousAndNextMoves() }
    previousNodeMoves.addNextMove(move)
    val newNodeLinkedMoves =
      movesCache.getOrPut(game.position.toImmutablePosition()) { PreviousAndNextMoves() }
    newNodeLinkedMoves.addPreviousMove(move)
    val newNode =
      Node(
        game.position.toImmutablePosition(),
        previous = previous,
        linkedMoves = newNodeLinkedMoves,
      )
    previous.addChild(move, newNode)
    return newNode
  }

  fun clearNextMoves(positionKey: PositionKey) {
    movesCache[positionKey]?.nextMoves?.clear()
    LOGGER.i { "Cleared next moves for position: $positionKey" }
  }

  fun clearPreviousMove(positionKey: PositionKey, move: String) {
    movesCache[positionKey]?.previousMoves?.remove(move)
    LOGGER.i { "Cleared previous move $move for position: $positionKey" }
  }

  /** Resets the cache from the database. */
  suspend fun resetCacheFromDataBase() {
    movesCache.clear()
    databaseRetrieved = false
    retrieveGraphFromDatabase()
  }

  /**
   * Retrieves the graph from the database.
   *
   * This function retrieves all positions from the database and populates the `movesCache` with
   * previous and next moves for each position.
   */
  suspend fun retrieveGraphFromDatabase() {
    if (databaseRetrieved) {
      LOGGER.i { "Database already retrieved." }
      return
    }
    val allPosition: List<IStoredNode> = (DatabaseHolder.getDatabase()).getAllPositions()
    allPosition.forEach {
      movesCache.getOrPut(it.positionKey) {
        PreviousAndNextMoves(it.getPreviousMoveList(), it.getAvailableMoveList())
      }
      LOGGER.i { "Retrieved node: ${it.positionKey}" }
    }
    databaseRetrieved = true
  }

  private val LOGGER = logging()
}
