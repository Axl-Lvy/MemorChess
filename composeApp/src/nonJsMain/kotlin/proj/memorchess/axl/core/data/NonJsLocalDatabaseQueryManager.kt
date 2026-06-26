package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.TrainingEntry
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Room backed [DatabaseQueryManager] for non-JS platforms.
 *
 * Takes its [CustomDatabase] explicitly so the production singleton wires the shared file while
 * tests can drive an isolated database through the same public API.
 */
internal class NonJsLocalDatabaseQueryManager(private val database: CustomDatabase) :
  DatabaseQueryManager {

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    val allNodes = database.getNodeEntityDao().getAllNodes()
    return (if (withDeletedOnes) allNodes else allNodes.filter { !it.node.isDeleted }).map {
      it.toStoredNode()
    }
  }

  override suspend fun getPosition(positionKey: PositionKey): DataNode? {
    return database.getNodeEntityDao().getNode(positionKey.value)?.toStoredNode()
  }

  override suspend fun deletePosition(position: PositionKey, mode: DeleteMode) {
    val dao = database.getNodeEntityDao()
    when (mode) {
      DeleteMode.HARD -> {
        dao.hardDeleteMoveFrom(position.value)
        dao.hardDeleteMoveTo(position.value)
        dao.hardDeleteNode(position.value)
      }
      DeleteMode.SOFT -> {
        dao.softDeleteNode(position.value)
        dao.softDeleteMoveFrom(position.value)
        dao.softDeleteMoveTo(position.value)
      }
    }
  }

  override suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode) {
    val dao = database.getNodeEntityDao()
    when (mode) {
      DeleteMode.HARD -> dao.hardDeleteMove(origin.value, move)
      DeleteMode.SOFT -> dao.softDeleteMove(origin.value, move)
    }
  }

  override suspend fun eraseAll() {
    val dao = database.getNodeEntityDao()
    dao.eraseAllMoves()
    dao.eraseAllNodes()
  }

  override suspend fun insertNodes(vararg positions: DataNode) {
    database
      .getNodeEntityDao()
      .insertNodeAndMoves(positions.map { NodeWithMoves.convertToEntity(it) })
  }

  override suspend fun getLastUpdate(): Instant? {
    val move = database.getNodeEntityDao().getLastMoveUpdate()
    val node = database.getNodeEntityDao().getLastNodeUpdate()
    return (if (move != null && node != null) {
        move.coerceAtLeast(node)
      } else {
        move ?: node
      })
      ?.truncateToSeconds()
  }

  override suspend fun nextReadyLearningCard(now: Instant): TrainingEntry? =
    database.getNodeEntityDao().nextReadyLearningCard(now)?.toTrainingEntry()

  override suspend fun nextPendingLearningCard(now: Instant): TrainingEntry? =
    database.getNodeEntityDao().nextPendingLearningCard(now)?.toTrainingEntry()

  override suspend fun nextDueReviewCard(dayEndExclusive: Instant): TrainingEntry? =
    database.getNodeEntityDao().nextDueReviewCard(dayEndExclusive)?.toTrainingEntry()

  override suspend fun nextDueNewCard(dayEndExclusive: Instant): TrainingEntry? =
    database.getNodeEntityDao().nextDueNewCard(dayEndExclusive)?.toTrainingEntry()

  override suspend fun getSchedulingCounts(
    dayStart: Instant,
    dayEndExclusive: Instant,
  ): SchedulingCounts = database.getNodeEntityDao().getSchedulingCounts(dayStart, dayEndExclusive)

  override suspend fun findEligibleAmong(
    keys: List<PositionKey>,
    dayEndExclusive: Instant,
  ): TrainingEntry? {
    if (keys.isEmpty()) return null
    val eligible =
      database
        .getNodeEntityDao()
        .eligibleAmong(keys.map { it.value }, dayEndExclusive)
        .associateBy { it.positionKey }
    // Preserve the caller's candidate order: return the first key that came back eligible.
    return keys.firstNotNullOfOrNull { eligible[it.value] }?.toTrainingEntry()
  }

  override suspend fun countDescendants(key: PositionKey, cap: Int): Int {
    if (cap <= 0) return 0
    val dao = database.getNodeEntityDao()
    if (!dao.nodeExists(key.value)) return 0
    var count = 1
    val visited = mutableSetOf(key.value)
    val queue = ArrayDeque<String>()
    queue.addLast(key.value)
    while (queue.isNotEmpty() && count < cap) {
      val origin = queue.removeFirst()
      for (child in dao.childrenOf(origin)) {
        if (child in visited) continue
        if (!dao.nodeExists(child)) continue
        // Convergent position reachable through another parent: a delete would keep it.
        if (dao.incomingCount(child) > 1) continue
        visited += child
        count++
        queue.addLast(child)
        if (count >= cap) break
      }
    }
    return count
  }

  /** Rebuilds a [TrainingEntry] from the lightweight projection, no edges loaded. */
  private fun NodeCardProjection.toTrainingEntry(): TrainingEntry =
    TrainingEntry(
      PositionKey(positionKey),
      CardState(
        dueDate = dueDate,
        lastReview = lastReview,
        firstReview = firstReview,
        stability = stability,
        difficulty = difficulty,
        reps = reps,
        lapses = lapses,
        phase = runCatching { CardPhase.valueOf(phase) }.getOrDefault(CardPhase.NEW),
        step = step,
      ),
    )
}

actual fun getPlatformSpecificLocalDatabase(): DatabaseQueryManager {
  return NonJsLocalDatabaseQueryManager(customDatabase)
}
