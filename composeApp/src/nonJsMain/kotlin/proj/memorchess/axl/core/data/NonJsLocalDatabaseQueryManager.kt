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

  override suspend fun getPosition(positionKey: PositionKey): DataNode? {
    return database.getNodeEntityDao().getNode(positionKey.value)?.toStoredNode()
  }

  override suspend fun getPositionIncludingDeleted(positionKey: PositionKey): DataNode? =
    database.getNodeEntityDao().getNodeIncludingDeleted(positionKey.value)?.toStoredNode()

  override suspend fun applyRemoteNode(node: DataNode) {
    database.getNodeEntityDao().insertNode(NodeWithMoves.convertToEntity(node).node)
  }

  override suspend fun applyRemoteMove(move: DataMove) {
    database.getNodeEntityDao().insertMoves(listOf(MoveEntity.convertToEntity(move)))
  }

  override suspend fun getNodesPage(cursor: String?, limit: Int): NodesPage {
    require(limit > 0) { "Page limit must be strictly positive, was $limit" }
    val dao = database.getNodeEntityDao()
    val rows = if (cursor == null) dao.getNodesPage(limit) else dao.getNodesPageAfter(cursor, limit)
    val nodes = rows.map { it.toStoredNode() }
    val nextCursor = if (nodes.size == limit) nodes.last().positionKey.value else null
    return NodesPage(nodes, nextCursor)
  }

  override suspend fun deletePosition(
    position: PositionKey,
    mode: DeleteMode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) {
    val dao = database.getNodeEntityDao()
    when (mode) {
      DeleteMode.HARD -> {
        dao.hardDeleteMoveFrom(position.value)
        dao.hardDeleteMoveTo(position.value)
        dao.hardDeleteNode(position.value)
      }
      DeleteMode.SOFT ->
        dao.softDeletePositionAndMarkDirty(position.value, updatedAt, originDevice, deviceSeq)
    }
  }

  override suspend fun deleteMove(
    origin: PositionKey,
    move: String,
    mode: DeleteMode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) {
    val dao = database.getNodeEntityDao()
    when (mode) {
      DeleteMode.HARD -> dao.hardDeleteMove(origin.value, move)
      DeleteMode.SOFT ->
        dao.softDeleteMoveAndMarkDirty(origin.value, move, updatedAt, originDevice, deviceSeq)
    }
  }

  override suspend fun eraseAll() {
    val dao = database.getNodeEntityDao()
    dao.eraseAllMoves()
    dao.eraseAllNodes()
    database.getOutboxDao().eraseAll()
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
    if (!database.getNodeEntityDao().nodeExists(key.value)) return 0
    return cappedDescendantCount(key, cap) { liveSingleParentChildren(it) }
  }

  /**
   * Non-deleted children of [origin] whose only non-deleted incoming edge comes from within the
   * subtree (incoming count at most one), resolved with point queries. A convergent position
   * reachable through an outside parent is excluded.
   */
  private suspend fun liveSingleParentChildren(origin: PositionKey): List<PositionKey> {
    val dao = database.getNodeEntityDao()
    val result = mutableListOf<PositionKey>()
    for (child in dao.childrenOf(origin.value)) {
      if (dao.nodeExists(child) && dao.incomingCount(child) <= 1) {
        result.add(PositionKey(child))
      }
    }
    return result
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

  override suspend fun markDirty(key: DirtyKey, deviceSeq: Long) {
    val (kind, key1, key2) = key.outboxKeyParts()
    database.getOutboxDao().upsert(kind, key1, key2, deviceSeq)
  }

  override suspend fun getOutbox(): List<OutboxEntry> =
    database.getOutboxDao().getAll().map { it.toOutboxEntry() }

  override suspend fun clearDirty(entries: Collection<OutboxEntry>) {
    database.getOutboxDao().clearIfNotNewer(entries.map { it.toEntity() })
  }
}

private fun DirtyKey.outboxKeyParts(): Triple<String, String, String> =
  when (this) {
    is DirtyKey.NodeKey -> Triple(OutboxEntryEntity.KIND_NODE, positionKey.value, "")
    is DirtyKey.EdgeKey -> Triple(OutboxEntryEntity.KIND_EDGE, origin.value, destination.value)
    is DirtyKey.SettingKey -> Triple(OutboxEntryEntity.KIND_SETTING, key, "")
  }

private fun OutboxEntry.toEntity(): OutboxEntryEntity {
  val (kind, key1, key2) = key.outboxKeyParts()
  return OutboxEntryEntity(kind, key1, key2, deviceSeq)
}

private fun OutboxEntryEntity.toDirtyKey(): DirtyKey =
  when (kind) {
    OutboxEntryEntity.KIND_NODE -> DirtyKey.NodeKey(PositionKey(key1))
    OutboxEntryEntity.KIND_EDGE -> DirtyKey.EdgeKey(PositionKey(key1), PositionKey(key2))
    OutboxEntryEntity.KIND_SETTING -> DirtyKey.SettingKey(key1)
    else -> error("Unknown outbox entry kind: $kind")
  }

private fun OutboxEntryEntity.toOutboxEntry(): OutboxEntry = OutboxEntry(toDirtyKey(), deviceSeq)

actual fun getPlatformSpecificLocalDatabase(): DatabaseQueryManager {
  return NonJsLocalDatabaseQueryManager(customDatabase)
}
