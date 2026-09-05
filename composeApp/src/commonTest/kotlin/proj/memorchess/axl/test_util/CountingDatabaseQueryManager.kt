package proj.memorchess.axl.test_util

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataEdgeRepertoireTag
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DataRepertoire
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.OutboxEntry
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

  override suspend fun getNodesPage(cursor: String?, limit: Int) =
    delegate.getNodesPage(cursor, limit)

  override suspend fun deletePosition(
    position: PositionKey,
    mode: DeleteMode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) = delegate.deletePosition(position, mode, originDevice, deviceSeq, updatedAt)

  override suspend fun deleteMove(
    origin: PositionKey,
    move: String,
    mode: DeleteMode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) = delegate.deleteMove(origin, move, mode, originDevice, deviceSeq, updatedAt)

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

  override suspend fun markDirty(key: DirtyKey, deviceSeq: Long) =
    delegate.markDirty(key, deviceSeq)

  override suspend fun getOutbox(): List<OutboxEntry> = delegate.getOutbox()

  override suspend fun clearDirty(entries: Collection<OutboxEntry>) = delegate.clearDirty(entries)

  override suspend fun getPositionIncludingDeleted(positionKey: PositionKey): DataNode? =
    delegate.getPositionIncludingDeleted(positionKey)

  override suspend fun applyRemoteNode(node: DataNode) = delegate.applyRemoteNode(node)

  override suspend fun applyRemoteMove(move: DataMove) = delegate.applyRemoteMove(move)

  override suspend fun getRepertoire(id: String): DataRepertoire? = delegate.getRepertoire(id)

  override suspend fun getRepertoireIncludingDeleted(id: String): DataRepertoire? =
    delegate.getRepertoireIncludingDeleted(id)

  override suspend fun getRepertoires(): List<DataRepertoire> = delegate.getRepertoires()

  override suspend fun insertRepertoire(repertoire: DataRepertoire) =
    delegate.insertRepertoire(repertoire)

  override suspend fun applyRemoteRepertoire(repertoire: DataRepertoire) =
    delegate.applyRemoteRepertoire(repertoire)

  override suspend fun getTags(
    origin: PositionKey,
    destination: PositionKey,
  ): List<DataEdgeRepertoireTag> = delegate.getTags(origin, destination)

  override suspend fun getTagIncludingDeleted(
    origin: PositionKey,
    destination: PositionKey,
    repertoireId: String,
  ): DataEdgeRepertoireTag? = delegate.getTagIncludingDeleted(origin, destination, repertoireId)

  override suspend fun insertTag(tag: DataEdgeRepertoireTag) = delegate.insertTag(tag)

  override suspend fun applyRemoteTag(tag: DataEdgeRepertoireTag) = delegate.applyRemoteTag(tag)
}
