package proj.memorchess.axl.microbenchmark

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.SchedulingCounts
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.TrainingEntry

/**
 * [DatabaseQueryManager] that persists nothing.
 *
 * Benchmarks plug it into [proj.memorchess.axl.core.graph.TreeStore] so that graph mutation
 * measurements cover the full store logic, including the conversion of nodes to their persisted
 * shape, without any platform database I/O skewing the numbers.
 */
class NoOpDatabaseQueryManager : DatabaseQueryManager {

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> = emptyList()

  override suspend fun getPosition(positionKey: PositionKey): DataNode? = null

  override suspend fun deletePosition(position: PositionKey, mode: DeleteMode) = Unit

  override suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode) = Unit

  override suspend fun eraseAll() = Unit

  override suspend fun insertNodes(vararg positions: DataNode) = Unit

  override suspend fun getLastUpdate(): Instant? = null

  override suspend fun nextReadyLearningCard(now: Instant): TrainingEntry? = null

  override suspend fun nextPendingLearningCard(now: Instant): TrainingEntry? = null

  override suspend fun nextDueReviewCard(dayEndExclusive: Instant): TrainingEntry? = null

  override suspend fun nextDueNewCard(dayEndExclusive: Instant): TrainingEntry? = null

  override suspend fun getSchedulingCounts(
    dayStart: Instant,
    dayEndExclusive: Instant,
  ): SchedulingCounts = SchedulingCounts(0, 0, 0, 0, 0)

  override suspend fun findEligibleAmong(
    keys: List<PositionKey>,
    dayEndExclusive: Instant,
  ): TrainingEntry? = null
}
