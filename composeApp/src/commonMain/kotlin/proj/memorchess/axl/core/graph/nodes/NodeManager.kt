package proj.memorchess.axl.core.graph.nodes

import com.diamondedge.logging.logging
import kotlinx.datetime.LocalDate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
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
    val position = Game().position.createIdentifier()
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
    nodeCache.clear()
    nodeCache.retrieveGraphFromDatabase()
  }

  /** Resets the cache from the database. */
  suspend fun resetCacheFromDataBase(db: DatabaseQueryManager) {
    nodeCache.clear()
    nodeCache.retrieveGraphFromDatabase(db)
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
    NodeCache.cacheNode(node)
  }

  private val LOGGER = logging()
}

/**
 * NodeCache singleton to abstract operations on the moves cache. This class manages the cache of
 * position keys and their associated moves.
 */
private object NodeCache : KoinComponent {

  private val database by inject<DatabaseQueryManager>()

  private lateinit var todayDate: LocalDate

  private var databaseRetrieved = false

  private val nodesByDay = mutableMapOf<Int, MutableMap<PositionIdentifier, StoredNode>>()

  /** Cache to prevent creating a node twice. */
  private val movesCache = mutableMapOf<PositionIdentifier, PreviousAndNextMoves>()

  /**
   * Gets or creates a PreviousAndNextMoves entry for the given position key.
   *
   * @param positionIdentifier The position key to get or create an entry for.
   * @param depth Depth at which the returned value will be at maximum. If necessary, the previous
   *   value will be updated.
   * @return The PreviousAndNextMoves for the given position key.
   */
  fun getOrCreate(positionIdentifier: PositionIdentifier, depth: Int): PreviousAndNextMoves {
    val prevValue = movesCache[positionIdentifier]
    if (prevValue == null) {
      val result = PreviousAndNextMoves(depth)
      movesCache[positionIdentifier] = result
      return result
    }
    if (prevValue.depth > depth) {
      prevValue.depth = depth
    }
    return prevValue
  }

  /**
   * Gets the PreviousAndNextMoves for the given position key.
   *
   * @param positionIdentifier The position key to get the moves for.
   * @return The PreviousAndNextMoves for the given position key, or null if not present.
   */
  fun get(positionIdentifier: PositionIdentifier): PreviousAndNextMoves? {
    return movesCache[positionIdentifier]
  }

  /**
   * Clears all next moves for the given position key.
   *
   * @param positionIdentifier The position key to clear next moves for.
   */
  fun clearNextMoves(positionIdentifier: PositionIdentifier) {
    movesCache[positionIdentifier]?.nextMoves?.clear()
    LOGGER.i { "Cleared next moves for position: $positionIdentifier" }
  }

  /**
   * Clears a specific previous move for the given position key.
   *
   * @param positionIdentifier The position key to clear the previous move for.
   * @param move The move to clear.
   */
  suspend fun clearPreviousMove(positionIdentifier: PositionIdentifier, move: StoredMove) {
    movesCache[positionIdentifier]?.previousMoves?.remove(move.move)
    LOGGER.i { "Cleared previous move $move for position: $positionIdentifier" }
    database.deleteMove(positionIdentifier.fenRepresentation, move.move)
  }

  /** Clears the cache and resets the database retrieved flag. */
  fun clear() {
    movesCache.clear()
    databaseRetrieved = false
  }

  fun cacheNode(node: StoredNode) {
    movesCache[node.positionIdentifier] = node.previousAndNextMoves
    val daysUntil = DateUtil.daysUntil(node.previousAndNextTrainingDate.nextDate)
    nodesByDay.getOrPut(daysUntil) { mutableMapOf() }[node.positionIdentifier] = node
  }

  fun getNodeFromDay(day: Int): StoredNode? {
    val candidates = nodesByDay[day] ?: return null
    val position =
      candidates.entries.minByOrNull { it.value.previousAndNextMoves.depth }?.key ?: return null
    return candidates.remove(position)
  }

  fun getNodeToTrainAfterPosition(day: Int, positionIdentifier: PositionIdentifier): StoredNode? {
    val todayNodes = nodesByDay[day] ?: return null
    for (candidatePosition in
      movesCache[positionIdentifier]?.nextMoves?.values?.map { it.destination } ?: emptyList()) {
      val candidateNode = todayNodes.remove(candidatePosition)
      if (candidateNode != null) {
        return candidateNode
      }
    }
    return null
  }

  fun getNumberOfNodesToTrain(day: Int): Int {
    return nodesByDay[day]?.size ?: 0
  }

  /** Retrieves the graph from the database and populates the cache. */
  suspend fun retrieveGraphFromDatabase(db: DatabaseQueryManager = database) {
    if (databaseRetrieved) {
      LOGGER.i { "Database already retrieved." }
      return
    }
    nodesByDay.clear()
    movesCache.clear()
    todayDate = DateUtil.today()
    val allNodes: List<StoredNode> = db.getAllNodes()
    allNodes.forEach { node ->
      movesCache.getOrPut(node.positionIdentifier) { node.previousAndNextMoves }
      if (node.previousAndNextMoves.nextMoves.any { it.value.isGood == true }) {
        val daysUntil = DateUtil.daysUntil(node.previousAndNextTrainingDate.nextDate)
        nodesByDay.getOrPut(daysUntil) { mutableMapOf() }[node.positionIdentifier] = node
      }
      LOGGER.i { "Retrieved node: ${node.positionIdentifier}" }
    }
    databaseRetrieved = true
  }

  private val LOGGER = logging()
}
