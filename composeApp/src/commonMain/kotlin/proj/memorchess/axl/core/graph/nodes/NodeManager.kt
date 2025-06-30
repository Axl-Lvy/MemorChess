package proj.memorchess.axl.core.graph.nodes

import com.diamondedge.logging.logging
import kotlinx.datetime.LocalDate
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.Game

/** Node factory singleton. */
object NodeManager {

  /** Reference to the NodeCache singleton. */
  private val nodeCache = NodeCache

  /**
   * Creates the root node.
   *
   * @return The root node.
   */
  fun createRootNode(): Node {
    val position = Game().position.toImmutablePosition()
    val rootNodeMoves = nodeCache.getOrPut(position) { PreviousAndNextMoves() }
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
    val previousNodeMoves = nodeCache.getOrPut(previous.position) { PreviousAndNextMoves() }
    val storedMove =
      previousNodeMoves.nextMoves.getOrPut(move) {
        StoredMove(previous.position, game.position.toImmutablePosition(), move)
      }
    val newNodeLinkedMoves =
      nodeCache.getOrPut(game.position.toImmutablePosition()) { PreviousAndNextMoves() }
    val previouslyStoredPreviousNode = newNodeLinkedMoves.addPreviousMove(storedMove)
    if (previouslyStoredPreviousNode != null && previouslyStoredPreviousNode != storedMove) {
      LOGGER.w { "Overwriting previous move: $previouslyStoredPreviousNode with $storedMove" }
    }
    val newNode =
      Node(
        game.position.toImmutablePosition(),
        previous = previous,
        linkedMoves = newNodeLinkedMoves,
      )
    previous.addChild(storedMove, newNode)
    return newNode
  }

  suspend fun clearNextMoves(positionKey: PositionKey) {
    nodeCache.clearNextMoves(positionKey)
  }

  suspend fun clearPreviousMove(positionKey: PositionKey, move: StoredMove) {
    nodeCache.clearPreviousMove(positionKey, move)
  }

  /** Resets the cache from the database. */
  suspend fun resetCacheFromDataBase() {
    nodeCache.clear()
    nodeCache.retrieveGraphFromDatabase()
  }

  fun getNextNodeToLearn(day: Int): StoredNode? {
    return nodeCache.getNodeFromDay(day)
  }

  fun cacheNode(node: StoredNode) {
    NodeCache.cacheNode(node)
  }

  private val LOGGER = logging()
}

/**
 * NodeCache singleton to abstract operations on the moves cache. This class manages the cache of
 * position keys and their associated moves.
 */
private object NodeCache {
  private lateinit var todayDate: LocalDate

  private var databaseRetrieved = false

  private val nodesByDay = mutableMapOf<Int, MutableList<StoredNode>>()

  /** Cache to prevent creating a node twice. */
  private val movesCache = mutableMapOf<PositionKey, PreviousAndNextMoves>()

  /**
   * Gets or creates a PreviousAndNextMoves entry for the given position key.
   *
   * @param positionKey The position key to get or create an entry for.
   * @param defaultValue A function that provides a default value if the key is not present.
   * @return The PreviousAndNextMoves for the given position key.
   */
  fun getOrPut(
    positionKey: PositionKey,
    defaultValue: () -> PreviousAndNextMoves,
  ): PreviousAndNextMoves {
    return movesCache.getOrPut(positionKey, defaultValue)
  }

  /**
   * Gets the PreviousAndNextMoves for the given position key.
   *
   * @param positionKey The position key to get the moves for.
   * @return The PreviousAndNextMoves for the given position key, or null if not present.
   */
  fun get(positionKey: PositionKey): PreviousAndNextMoves? {
    return movesCache[positionKey]
  }

  /**
   * Clears all next moves for the given position key.
   *
   * @param positionKey The position key to clear next moves for.
   */
  suspend fun clearNextMoves(positionKey: PositionKey) {
    movesCache[positionKey]?.nextMoves?.clear()
    LOGGER.i { "Cleared next moves for position: $positionKey" }
    DatabaseHolder.getDatabase().deleteMoveFrom(positionKey.fenRepresentation)
  }

  /**
   * Clears a specific previous move for the given position key.
   *
   * @param positionKey The position key to clear the previous move for.
   * @param move The move to clear.
   */
  suspend fun clearPreviousMove(positionKey: PositionKey, move: StoredMove) {
    movesCache[positionKey]?.previousMoves?.remove(move.move)
    LOGGER.i { "Cleared previous move $move for position: $positionKey" }
    DatabaseHolder.getDatabase().deleteMove(positionKey.fenRepresentation, move.move)
  }

  /** Clears the cache and resets the database retrieved flag. */
  fun clear() {
    movesCache.clear()
    databaseRetrieved = false
  }

  fun cacheNode(node: StoredNode) {
    movesCache[node.positionKey] = node.previousAndNextMoves
    val daysUntil = DateUtil.daysUntil(node.previousAndNextTrainingDate.nextDate)
    nodesByDay.getOrPut(daysUntil) { mutableListOf() }.add(node)
  }

  fun getNodeFromDay(day: Int): StoredNode? {
    return nodesByDay[day]?.removeFirstOrNull()
  }

  /** Retrieves the graph from the database and populates the cache. */
  suspend fun retrieveGraphFromDatabase() {
    if (databaseRetrieved) {
      LOGGER.i { "Database already retrieved." }
      return
    }
    nodesByDay.clear()
    todayDate = DateUtil.today()
    val allNodes: List<StoredNode> = DatabaseHolder.getDatabase().getAllPositions()
    allNodes.forEach { node ->
      movesCache.getOrPut(node.positionKey) { node.previousAndNextMoves }
      if (node.previousAndNextMoves.nextMoves.any { it.value.isGood == true }) {
        val daysUntil = DateUtil.daysUntil(node.previousAndNextTrainingDate.nextDate)
        nodesByDay.getOrPut(daysUntil) { mutableListOf() }.add(node)
      }
      LOGGER.i { "Retrieved node: ${node.positionKey}" }
    }
    databaseRetrieved = true
  }

  private val LOGGER = logging()
}
