package proj.memorchess.axl.test_util

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.SchedulingCounts
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.TrainingEntry

/**
 * [DatabaseQueryManager] decorator that counts how many times each position is point looked up via
 * [getPosition], so cache tests can assert hit / miss behaviour and prefetch fan out. Every other
 * operation delegates to [delegate] unchanged.
 *
 * @property delegate Backing manager, normally an
 *   [proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager].
 */
class CountingDatabaseQueryManager(private val delegate: DatabaseQueryManager) :
  DatabaseQueryManager {

  /** Number of [getPosition] calls received per position key. */
  val getPositionCalls: MutableMap<PositionKey, Int> = mutableMapOf()

  /** Total number of [getPosition] calls across every key. */
  val totalGetPositionCalls: Int
    get() = getPositionCalls.values.sum()

  override suspend fun getPosition(positionKey: PositionKey): DataNode? {
    getPositionCalls[positionKey] = (getPositionCalls[positionKey] ?: 0) + 1
    return delegate.getPosition(positionKey)
  }

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> =
    delegate.getAllNodes(withDeletedOnes)

  override suspend fun deletePosition(position: PositionKey, mode: DeleteMode) =
    delegate.deletePosition(position, mode)

  override suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode) =
    delegate.deleteMove(origin, move, mode)

  override suspend fun eraseAll() = delegate.eraseAll()

  override suspend fun insertNodes(vararg positions: DataNode) = delegate.insertNodes(*positions)

  override suspend fun getLastUpdate(): Instant? = delegate.getLastUpdate()

  override suspend fun nextReadyLearningCard(now: Instant): TrainingEntry? =
    delegate.nextReadyLearningCard(now)

  override suspend fun nextPendingLearningCard(now: Instant): TrainingEntry? =
    delegate.nextPendingLearningCard(now)

  override suspend fun nextDueReviewCard(dayEndExclusive: Instant): TrainingEntry? =
    delegate.nextDueReviewCard(dayEndExclusive)

  override suspend fun nextDueNewCard(dayEndExclusive: Instant): TrainingEntry? =
    delegate.nextDueNewCard(dayEndExclusive)

  override suspend fun getSchedulingCounts(
    dayStart: Instant,
    dayEndExclusive: Instant,
  ): SchedulingCounts = delegate.getSchedulingCounts(dayStart, dayEndExclusive)

  override suspend fun findEligibleAmong(
    keys: List<PositionKey>,
    dayEndExclusive: Instant,
  ): TrainingEntry? = delegate.findEligibleAmong(keys, dayEndExclusive)

  override suspend fun countDescendants(key: PositionKey, cap: Int): Int =
    delegate.countDescendants(key, cap)
}
