package proj.memorchess.axl.core.graph.nodes

import com.diamondedge.logging.logging
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.IStoredMove
import proj.memorchess.axl.core.data.IStoredNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.engine.Game

/** Node factory singleton. */
object NodeFactory {

  private var databaseRetrieved = false

  /** Cache to prevent creating a node twice. */
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
    val storedMove = StoredMove(previous.position, game.position.toImmutablePosition(), move)
    previousNodeMoves.addNextMove(storedMove)
    val newNodeLinkedMoves =
      movesCache.getOrPut(game.position.toImmutablePosition()) { PreviousAndNextMoves() }
    newNodeLinkedMoves.addPreviousMove(storedMove)
    val newNode =
      Node(
        game.position.toImmutablePosition(),
        previous = previous,
        linkedMoves = newNodeLinkedMoves,
      )
    previous.addChild(storedMove, newNode)
    return newNode
  }

  fun clearNextMoves(positionKey: PositionKey) {
    movesCache[positionKey]?.nextMoves?.clear()
    LOGGER.i { "Cleared next moves for position: $positionKey" }
  }

  fun clearPreviousMove(positionKey: PositionKey, move: IStoredMove) {
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
      movesCache.getOrPut(it.positionKey) { PreviousAndNextMoves(it.previousMoves, it.nextMoves) }
      LOGGER.i { "Retrieved node: ${it.positionKey}" }
    }
    databaseRetrieved = true
  }

  private val LOGGER = logging()
}
