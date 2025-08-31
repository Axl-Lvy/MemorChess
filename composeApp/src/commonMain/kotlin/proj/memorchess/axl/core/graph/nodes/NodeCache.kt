package proj.memorchess.axl.core.graph.nodes

import com.diamondedge.logging.logging
import kotlin.collections.set
import kotlinx.datetime.LocalDate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil

/**
 * NodeCache singleton to abstract operations on the moves cache. This class manages the cache of
 * position keys and their associated moves.
 */
class NodeCache : KoinComponent {

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
    database.deleteMove(positionIdentifier, move.move)
  }

  /** Clears the cache and resets the database retrieved flag. */
  private fun clear() {
    nodesByDay.clear()
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
  suspend fun resetGraphFromDatabase(db: DatabaseQueryManager = database) {
    clear()
    todayDate = DateUtil.today()
    val allNodes: List<StoredNode> = db.getAllNodes()
    allNodes.forEach { node ->
      val previousAndNextMoves = node.previousAndNextMoves.filterNotDeleted()
      movesCache.getOrPut(node.positionIdentifier) { previousAndNextMoves }
      if (previousAndNextMoves.nextMoves.any { it.value.isGood == true }) {
        val daysUntil = DateUtil.daysUntil(node.previousAndNextTrainingDate.nextDate)
        nodesByDay.getOrPut(daysUntil) { mutableMapOf() }[node.positionIdentifier] = node
      }
      LOGGER.i { "Retrieved node: ${node.positionIdentifier}" }
    }
    databaseRetrieved = true
  }
}

private val LOGGER = logging()
