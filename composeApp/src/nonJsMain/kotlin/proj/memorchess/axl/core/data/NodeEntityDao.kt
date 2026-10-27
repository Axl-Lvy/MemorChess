package proj.memorchess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlin.time.Instant

/** DAO for managing [NodeEntity] and [MoveEntity] (linked by [NodeWithMoves]). */
@Dao
interface NodeEntityDao {

  /**
   * Inserts or replaces [nodes] with their moves, queuing each node's outbox entry in the same
   * transaction.
   */
  @Transaction
  suspend fun insertNodeAndMoves(nodes: Collection<NodeWithMoves>) {
    nodes.forEach {
      insertNode(it.node)
      insertMoves(it.nextMoves + it.previousMoves)
      upsertOutboxEntry(
        OutboxEntryEntity.KIND_NODE,
        it.node.positionKey,
        deviceSeq = it.node.deviceSeq,
      )
    }
  }

  /**
   * Queues an outbox entry, keeping the higher `deviceSeq` on a repeat mark. Duplicates
   * [OutboxDao.upsert] because Room cannot span one `@Transaction` across two `@Dao` interfaces.
   */
  @Query(
    "INSERT INTO OutboxEntryEntity (kind, key1, key2, key3, deviceSeq) " +
      "VALUES (:kind, :key1, :key2, :key3, :deviceSeq) " +
      "ON CONFLICT(kind, key1, key2, key3) DO UPDATE SET deviceSeq = MAX(deviceSeq, excluded.deviceSeq)"
  )
  suspend fun upsertOutboxEntry(
    kind: String,
    key1: String,
    key2: String = "",
    key3: String = "",
    deviceSeq: Long,
  )

  /** Inserts or replaces a node. */
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNode(item: NodeEntity)

  /** Inserts or replaces moves. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMoves(items: Collection<MoveEntity>)

  /** Soft deletes a move and stamps the sync fields. */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE, ownIsGood = NULL, updatedAt = :updatedAt, originDevice = :originDevice, deviceSeq = :deviceSeq " +
      "WHERE isDeleted IS FALSE AND origin = :origin AND move = :move"
  )
  suspend fun softDeleteMove(
    origin: String,
    move: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  )

  /** Live destination of [origin]/[move], or `null` when missing or already deleted. */
  @Query(
    "SELECT destination FROM MoveEntity WHERE isDeleted = 0 AND origin = :origin AND move = :move LIMIT 1"
  )
  suspend fun liveMoveDestination(origin: String, move: String): String?

  /**
   * Soft deletes [origin]/[move] and queues its edge for push in one transaction. A no-op when the
   * move is missing or already deleted.
   */
  @Transaction
  suspend fun softDeleteMoveAndMarkDirty(
    origin: String,
    move: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  ) {
    // Read before the flip, while the edge is still live, so the outbox names the tombstoned edge.
    val destination = liveMoveDestination(origin, move) ?: return
    softDeleteMove(origin, move, updatedAt, originDevice, deviceSeq)
    upsertOutboxEntry(OutboxEntryEntity.KIND_EDGE, origin, destination, deviceSeq = deviceSeq)
  }

  /** Soft deletes all moves leaving [origin] and stamps the sync fields. */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE, ownIsGood = NULL, updatedAt = :updatedAt, originDevice = :originDevice, deviceSeq = :deviceSeq " +
      "WHERE isDeleted IS FALSE AND origin = :origin"
  )
  suspend fun softDeleteMoveFrom(
    origin: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  )

  /** Soft deletes all moves arriving at [destination] and stamps the sync fields. */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE, ownIsGood = NULL, updatedAt = :updatedAt, originDevice = :originDevice, deviceSeq = :deviceSeq " +
      "WHERE isDeleted IS FALSE AND destination = :destination"
  )
  suspend fun softDeleteMoveTo(
    destination: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  )

  @Transaction
  @Query("SELECT * FROM NodeEntity WHERE positionKey = :positionKey AND isDeleted IS FALSE")
  suspend fun getNode(positionKey: String): NodeWithMoves?

  /** [getNode] including tombstones, for conflict resolution against pulled sync rows. */
  @Transaction
  @Query("SELECT * FROM NodeEntity WHERE positionKey = :positionKey")
  suspend fun getNodeIncludingDeleted(positionKey: String): NodeWithMoves?

  /**
   * Soft deletes a node row and stamps the sync fields.
   *
   * @return The number of rows flipped: `1` when the node was live, `0` when it was missing or
   *   already deleted.
   */
  @Query(
    "UPDATE NodeEntity SET isDeleted = TRUE, updatedAt = :updatedAt, originDevice = :originDevice, deviceSeq = :deviceSeq " +
      "WHERE isDeleted IS FALSE AND positionKey = :positionKey"
  )
  suspend fun softDeleteNode(
    positionKey: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  ): Int

  /** Live origins of every non deleted move arriving at [destination]. */
  @Query("SELECT origin FROM MoveEntity WHERE isDeleted = 0 AND destination = :destination")
  suspend fun liveMoveOrigins(destination: String): List<String>

  /**
   * Soft deletes [positionKey]'s node and every incident move, queuing each tombstoned row for push
   * in the same transaction.
   */
  @Transaction
  suspend fun softDeletePositionAndMarkDirty(
    positionKey: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  ) {
    // Read before the flips, so the outbox names exactly the edges this call tombstones.
    val outgoing = childrenOf(positionKey)
    val incoming = liveMoveOrigins(positionKey)
    val nodeChanged = softDeleteNode(positionKey, updatedAt, originDevice, deviceSeq) > 0
    softDeleteMoveFrom(positionKey, updatedAt, originDevice, deviceSeq)
    softDeleteMoveTo(positionKey, updatedAt, originDevice, deviceSeq)
    if (nodeChanged)
      upsertOutboxEntry(OutboxEntryEntity.KIND_NODE, positionKey, deviceSeq = deviceSeq)
    for (destination in outgoing) {
      upsertOutboxEntry(
        OutboxEntryEntity.KIND_EDGE,
        positionKey,
        destination,
        deviceSeq = deviceSeq,
      )
    }
    for (origin in incoming) {
      upsertOutboxEntry(OutboxEntryEntity.KIND_EDGE, origin, positionKey, deviceSeq = deviceSeq)
    }
  }

  /** Hard wipes every node row. */
  @Query("DELETE FROM NodeEntity") suspend fun eraseAllNodes()

  /** Hard wipes every move row. */
  @Query("DELETE FROM MoveEntity") suspend fun eraseAllMoves()

  /**
   * First bounded page of non deleted nodes ordered by position key ascending. See
   * [DatabaseQueryManager.getNodesPage].
   */
  @Transaction
  @Query("SELECT * FROM NodeEntity WHERE isDeleted IS FALSE ORDER BY positionKey ASC LIMIT :limit")
  suspend fun getNodesPage(limit: Int): List<NodeWithMoves>

  /**
   * Bounded page of non deleted nodes whose position key is strictly greater than [cursor], ordered
   * by position key ascending. See [DatabaseQueryManager.getNodesPage].
   */
  @Transaction
  @Query(
    "SELECT * FROM NodeEntity WHERE isDeleted IS FALSE AND positionKey > :cursor " +
      "ORDER BY positionKey ASC LIMIT :limit"
  )
  suspend fun getNodesPageAfter(cursor: String, limit: Int): List<NodeWithMoves>

  @Query("SELECT MAX(updatedAt) FROM NodeEntity") suspend fun getLastNodeUpdate(): Instant?

  @Query("SELECT MAX(updatedAt) FROM MoveEntity") suspend fun getLastMoveUpdate(): Instant?

  /**
   * Next ready in session card: mid learning and due on or before [now], earliest due first. See
   * [DatabaseQueryManager.nextReadyLearningCard].
   */
  @Query(
    "SELECT positionKey, dueDate, lastReview, firstReview, stability, difficulty, reps, lapses, phase, step " +
      "FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 " +
      "AND phase IN ('LEARNING', 'RELEARNING') AND dueDate <= :now " +
      "AND (:repertoireId IS NULL OR EXISTS(SELECT 1 FROM NodeRepertoireTrainableEntity t " +
      "WHERE t.positionKey = NodeEntity.positionKey AND t.repertoireId = :repertoireId)) " +
      "ORDER BY dueDate ASC LIMIT 1"
  )
  suspend fun nextReadyLearningCard(now: Instant, repertoireId: String? = null): NodeCardProjection?

  /**
   * Next pending in session card: mid learning and due strictly after [now], earliest due first.
   * See [DatabaseQueryManager.nextPendingLearningCard].
   */
  @Query(
    "SELECT positionKey, dueDate, lastReview, firstReview, stability, difficulty, reps, lapses, phase, step " +
      "FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 " +
      "AND phase IN ('LEARNING', 'RELEARNING') AND dueDate > :now " +
      "AND (:repertoireId IS NULL OR EXISTS(SELECT 1 FROM NodeRepertoireTrainableEntity t " +
      "WHERE t.positionKey = NodeEntity.positionKey AND t.repertoireId = :repertoireId)) " +
      "ORDER BY dueDate ASC LIMIT 1"
  )
  suspend fun nextPendingLearningCard(
    now: Instant,
    repertoireId: String? = null,
  ): NodeCardProjection?

  /**
   * Next due review card: graduated and due before [dayEndExclusive], shallowest first. See
   * [DatabaseQueryManager.nextDueReviewCard].
   */
  @Query(
    "SELECT positionKey, dueDate, lastReview, firstReview, stability, difficulty, reps, lapses, phase, step " +
      "FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 AND phase = 'REVIEW' " +
      "AND dueDate < :dayEndExclusive " +
      "AND (:repertoireId IS NULL OR EXISTS(SELECT 1 FROM NodeRepertoireTrainableEntity t " +
      "WHERE t.positionKey = NodeEntity.positionKey AND t.repertoireId = :repertoireId)) " +
      "ORDER BY depth ASC LIMIT 1"
  )
  suspend fun nextDueReviewCard(
    dayEndExclusive: Instant,
    repertoireId: String? = null,
  ): NodeCardProjection?

  /**
   * Next due new card: brand new and due before [dayEndExclusive], shallowest then earliest created
   * first. See [DatabaseQueryManager.nextDueNewCard].
   */
  @Query(
    "SELECT positionKey, dueDate, lastReview, firstReview, stability, difficulty, reps, lapses, phase, step " +
      "FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 AND phase = 'NEW' " +
      "AND dueDate < :dayEndExclusive " +
      "AND (:repertoireId IS NULL OR EXISTS(SELECT 1 FROM NodeRepertoireTrainableEntity t " +
      "WHERE t.positionKey = NodeEntity.positionKey AND t.repertoireId = :repertoireId)) " +
      "ORDER BY depth ASC, createdAt ASC LIMIT 1"
  )
  suspend fun nextDueNewCard(
    dayEndExclusive: Instant,
    repertoireId: String? = null,
  ): NodeCardProjection?

  /** Count of cards first reviewed within the day window. */
  @Query(
    "SELECT COUNT(*) FROM NodeEntity WHERE isDeleted = 0 " +
      "AND firstReview >= :dayStart AND firstReview < :dayEndExclusive"
  )
  suspend fun countIntroducedBetween(dayStart: Instant, dayEndExclusive: Instant): Int

  /** Count of cards last reviewed within the day window. */
  @Query(
    "SELECT COUNT(*) FROM NodeEntity WHERE isDeleted = 0 " +
      "AND lastReview >= :dayStart AND lastReview < :dayEndExclusive"
  )
  suspend fun countTrainedBetween(dayStart: Instant, dayEndExclusive: Instant): Int

  /** Count of trainable graduated cards due before [dayEndExclusive], optionally scoped. */
  @Query(
    "SELECT COUNT(*) FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 " +
      "AND phase = 'REVIEW' AND dueDate < :dayEndExclusive " +
      "AND (:repertoireId IS NULL OR EXISTS(SELECT 1 FROM NodeRepertoireTrainableEntity t " +
      "WHERE t.positionKey = NodeEntity.positionKey AND t.repertoireId = :repertoireId))"
  )
  suspend fun countDueReviews(dayEndExclusive: Instant, repertoireId: String? = null): Int

  /** Count of trainable brand new cards due before [dayEndExclusive], optionally scoped. */
  @Query(
    "SELECT COUNT(*) FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 " +
      "AND phase = 'NEW' AND dueDate < :dayEndExclusive " +
      "AND (:repertoireId IS NULL OR EXISTS(SELECT 1 FROM NodeRepertoireTrainableEntity t " +
      "WHERE t.positionKey = NodeEntity.positionKey AND t.repertoireId = :repertoireId))"
  )
  suspend fun countDueNew(dayEndExclusive: Instant, repertoireId: String? = null): Int

  /** Count of trainable cards currently mid learning, optionally scoped. */
  @Query(
    "SELECT COUNT(*) FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 " +
      "AND phase IN ('LEARNING', 'RELEARNING') " +
      "AND (:repertoireId IS NULL OR EXISTS(SELECT 1 FROM NodeRepertoireTrainableEntity t " +
      "WHERE t.positionKey = NodeEntity.positionKey AND t.repertoireId = :repertoireId))"
  )
  suspend fun countInSession(repertoireId: String? = null): Int

  /**
   * Computes the day's five scheduling tallies in one transaction. They are unscoped, so the daily
   * caps stay global whatever the repertoire.
   */
  @Transaction
  suspend fun getSchedulingCounts(dayStart: Instant, dayEndExclusive: Instant): SchedulingCounts =
    SchedulingCounts(
      introducedToday = countIntroducedBetween(dayStart, dayEndExclusive),
      trainedToday = countTrainedBetween(dayStart, dayEndExclusive),
      dueReviews = countDueReviews(dayEndExclusive),
      dueNew = countDueNew(dayEndExclusive),
      inSession = countInSession(),
    )

  /** See [DatabaseQueryManager.getScopedCounts]. */
  @Transaction
  suspend fun getScopedCounts(
    dayEndExclusive: Instant,
    repertoireId: String,
  ): ScopedSchedulingCounts =
    ScopedSchedulingCounts(
      dueReviews = countDueReviews(dayEndExclusive, repertoireId),
      dueNew = countDueNew(dayEndExclusive, repertoireId),
      inSession = countInSession(repertoireId),
    )

  /**
   * Unordered eligible projections among [keys]: trainable and either mid learning or due before
   * [dayEndExclusive].
   */
  @Query(
    "SELECT positionKey, dueDate, lastReview, firstReview, stability, difficulty, reps, lapses, phase, step " +
      "FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 AND positionKey IN (:keys) " +
      "AND (phase IN ('LEARNING', 'RELEARNING') OR dueDate < :dayEndExclusive) " +
      "AND (:repertoireId IS NULL OR EXISTS(SELECT 1 FROM NodeRepertoireTrainableEntity t " +
      "WHERE t.positionKey = NodeEntity.positionKey AND t.repertoireId = :repertoireId))"
  )
  suspend fun eligibleAmong(
    keys: List<String>,
    dayEndExclusive: Instant,
    repertoireId: String? = null,
  ): List<NodeCardProjection>

  /** Destinations of every non deleted move leaving [origin]. */
  @Query("SELECT destination FROM MoveEntity WHERE isDeleted = 0 AND origin = :origin")
  suspend fun childrenOf(origin: String): List<String>

  /** Number of non deleted moves arriving at [destination]. */
  @Query("SELECT COUNT(*) FROM MoveEntity WHERE isDeleted = 0 AND destination = :destination")
  suspend fun incomingCount(destination: String): Int

  /** Whether a non deleted node row exists for [positionKey]. */
  @Query(
    "SELECT EXISTS(SELECT 1 FROM NodeEntity WHERE isDeleted = 0 AND positionKey = :positionKey)"
  )
  suspend fun nodeExists(positionKey: String): Boolean
}
