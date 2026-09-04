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
   * Inserts a new node with its moves into the database, queuing each node's own outbox entry in
   * the same transaction as the row write it names.
   *
   * If the node already exists, it will be replaced. Same for the moves.
   *
   * @param nodes The node with its next and previous moves to insert.
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
   * Queues an outbox entry, keeping the higher of the stored and new `deviceSeq` on a repeat mark.
   * Duplicates [OutboxDao.upsert]'s query so it can share a `@Transaction` with the row write it
   * names, since Room cannot span one `@Transaction` default method across two `@Dao` interfaces.
   */
  @Query(
    "INSERT INTO OutboxEntryEntity (kind, key1, key2, deviceSeq) VALUES (:kind, :key1, :key2, :deviceSeq) " +
      "ON CONFLICT(kind, key1, key2) DO UPDATE SET deviceSeq = MAX(deviceSeq, excluded.deviceSeq)"
  )
  suspend fun upsertOutboxEntry(kind: String, key1: String, key2: String = "", deviceSeq: Long)

  /**
   * Inserts a new node into the database.
   *
   * If the node already exists, it will be replaced.
   *
   * @param item The node to insert.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNode(item: NodeEntity)

  /**
   * Inserts moves into the database.
   *
   * If a move already exists, it will be replaced.
   *
   * @param items The collection of moves to insert.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMoves(items: Collection<MoveEntity>)

  /**
   * Soft deletes a move by flipping its `isDeleted` flag and stamping the sync fields.
   *
   * @param origin The move's origin.
   * @param move The move's notation.
   */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE, updatedAt = :updatedAt, originDevice = :originDevice, deviceSeq = :deviceSeq " +
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
   * Soft deletes [origin]/[move] and queues its edge for push, atomically: the destination is read
   * before the row is flipped so the outbox entry names the edge that was actually tombstoned, in
   * the same transaction as the flip. A no-op, including no outbox write, when the move is missing
   * or already deleted.
   */
  @Transaction
  suspend fun softDeleteMoveAndMarkDirty(
    origin: String,
    move: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  ) {
    val destination = liveMoveDestination(origin, move) ?: return
    softDeleteMove(origin, move, updatedAt, originDevice, deviceSeq)
    upsertOutboxEntry(OutboxEntryEntity.KIND_EDGE, origin, destination, deviceSeq)
  }

  /** Hard deletes a move row. */
  @Query("DELETE FROM MoveEntity WHERE origin = :origin AND move = :move")
  suspend fun hardDeleteMove(origin: String, move: String)

  /**
   * Soft deletes all moves leaving [origin] by flipping their `isDeleted` flag and stamping the
   * sync fields.
   *
   * @param origin The FEN string of the origin position.
   */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE, updatedAt = :updatedAt, originDevice = :originDevice, deviceSeq = :deviceSeq " +
      "WHERE isDeleted IS FALSE AND origin = :origin"
  )
  suspend fun softDeleteMoveFrom(
    origin: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  )

  /** Hard deletes every move row leaving [origin]. */
  @Query("DELETE FROM MoveEntity WHERE origin = :origin")
  suspend fun hardDeleteMoveFrom(origin: String)

  /**
   * Soft deletes all moves arriving at [destination] by flipping their `isDeleted` flag and
   * stamping the sync fields.
   *
   * @param destination The FEN string of the destination position.
   */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE, updatedAt = :updatedAt, originDevice = :originDevice, deviceSeq = :deviceSeq " +
      "WHERE isDeleted IS FALSE AND destination = :destination"
  )
  suspend fun softDeleteMoveTo(
    destination: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  )

  /** Hard deletes every move row arriving at [destination]. */
  @Query("DELETE FROM MoveEntity WHERE destination = :destination")
  suspend fun hardDeleteMoveTo(destination: String)

  @Transaction
  @Query("SELECT * FROM NodeEntity WHERE positionKey = :fen AND isDeleted IS FALSE")
  suspend fun getNode(fen: String): NodeWithMoves?

  /**
   * Soft deletes a node row by flipping its `isDeleted` flag and stamping the sync fields.
   *
   * @return The number of rows flipped: `1` when the node was live, `0` when it was missing or
   *   already deleted.
   */
  @Query(
    "UPDATE NodeEntity SET isDeleted = TRUE, updatedAt = :updatedAt, originDevice = :originDevice, deviceSeq = :deviceSeq " +
      "WHERE isDeleted IS FALSE AND positionKey = :fen"
  )
  suspend fun softDeleteNode(
    fen: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  ): Int

  /** Hard deletes a node row. */
  @Query("DELETE FROM NodeEntity WHERE positionKey = :fen") suspend fun hardDeleteNode(fen: String)

  /** Live origins of every non deleted move arriving at [destination]. */
  @Query("SELECT origin FROM MoveEntity WHERE isDeleted = 0 AND destination = :destination")
  suspend fun liveMoveOrigins(destination: String): List<String>

  /**
   * Soft deletes [fen]'s node and every incident move, queuing the node (when it was live) and each
   * tombstoned edge for push, all in the same transaction as the row changes: the incident live
   * edges are read before the flips so the outbox entries name exactly the edges tombstoned by this
   * call.
   */
  @Transaction
  suspend fun softDeletePositionAndMarkDirty(
    fen: String,
    updatedAt: Instant,
    originDevice: String,
    deviceSeq: Long,
  ) {
    val outgoing = childrenOf(fen)
    val incoming = liveMoveOrigins(fen)
    val nodeChanged = softDeleteNode(fen, updatedAt, originDevice, deviceSeq) > 0
    softDeleteMoveFrom(fen, updatedAt, originDevice, deviceSeq)
    softDeleteMoveTo(fen, updatedAt, originDevice, deviceSeq)
    if (nodeChanged) upsertOutboxEntry(OutboxEntryEntity.KIND_NODE, fen, deviceSeq = deviceSeq)
    for (destination in outgoing) {
      upsertOutboxEntry(OutboxEntryEntity.KIND_EDGE, fen, destination, deviceSeq)
    }
    for (origin in incoming) {
      upsertOutboxEntry(OutboxEntryEntity.KIND_EDGE, origin, fen, deviceSeq)
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
      "AND phase IN ('LEARNING', 'RELEARNING') AND dueDate <= :now ORDER BY dueDate ASC LIMIT 1"
  )
  suspend fun nextReadyLearningCard(now: Instant): NodeCardProjection?

  /**
   * Next pending in session card: mid learning and due strictly after [now], earliest due first.
   * See [DatabaseQueryManager.nextPendingLearningCard].
   */
  @Query(
    "SELECT positionKey, dueDate, lastReview, firstReview, stability, difficulty, reps, lapses, phase, step " +
      "FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 " +
      "AND phase IN ('LEARNING', 'RELEARNING') AND dueDate > :now ORDER BY dueDate ASC LIMIT 1"
  )
  suspend fun nextPendingLearningCard(now: Instant): NodeCardProjection?

  /**
   * Next due review card: graduated and due before [dayEndExclusive], shallowest first. See
   * [DatabaseQueryManager.nextDueReviewCard].
   */
  @Query(
    "SELECT positionKey, dueDate, lastReview, firstReview, stability, difficulty, reps, lapses, phase, step " +
      "FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 AND phase = 'REVIEW' " +
      "AND dueDate < :dayEndExclusive ORDER BY depth ASC LIMIT 1"
  )
  suspend fun nextDueReviewCard(dayEndExclusive: Instant): NodeCardProjection?

  /**
   * Next due new card: brand new and due before [dayEndExclusive], shallowest then earliest created
   * first. See [DatabaseQueryManager.nextDueNewCard].
   */
  @Query(
    "SELECT positionKey, dueDate, lastReview, firstReview, stability, difficulty, reps, lapses, phase, step " +
      "FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 AND phase = 'NEW' " +
      "AND dueDate < :dayEndExclusive ORDER BY depth ASC, createdAt ASC LIMIT 1"
  )
  suspend fun nextDueNewCard(dayEndExclusive: Instant): NodeCardProjection?

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

  /** Count of trainable graduated cards due before [dayEndExclusive]. */
  @Query(
    "SELECT COUNT(*) FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 " +
      "AND phase = 'REVIEW' AND dueDate < :dayEndExclusive"
  )
  suspend fun countDueReviews(dayEndExclusive: Instant): Int

  /** Count of trainable brand new cards due before [dayEndExclusive]. */
  @Query(
    "SELECT COUNT(*) FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 " +
      "AND phase = 'NEW' AND dueDate < :dayEndExclusive"
  )
  suspend fun countDueNew(dayEndExclusive: Instant): Int

  /** Count of trainable cards currently mid learning. */
  @Query(
    "SELECT COUNT(*) FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 " +
      "AND phase IN ('LEARNING', 'RELEARNING')"
  )
  suspend fun countInSession(): Int

  /**
   * Computes the day's five scheduling tallies in one transaction. See
   * [DatabaseQueryManager.getSchedulingCounts].
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

  /**
   * Eligible projections among an explicit bounded key set: trainable and either mid learning or
   * due before [dayEndExclusive]. The caller orders the result by candidate order. See
   * [DatabaseQueryManager.findEligibleAmong].
   */
  @Query(
    "SELECT positionKey, dueDate, lastReview, firstReview, stability, difficulty, reps, lapses, phase, step " +
      "FROM NodeEntity WHERE isDeleted = 0 AND hasGoodOutgoing = 1 AND positionKey IN (:keys) " +
      "AND (phase IN ('LEARNING', 'RELEARNING') OR dueDate < :dayEndExclusive)"
  )
  suspend fun eligibleAmong(keys: List<String>, dayEndExclusive: Instant): List<NodeCardProjection>

  /**
   * Destinations of every non deleted move leaving [origin]. Used by the bounded descendant count
   * to expand one node's children with a single indexed point query (`origin` is indexed). See
   * [DatabaseQueryManager.countDescendants].
   */
  @Query("SELECT destination FROM MoveEntity WHERE isDeleted = 0 AND origin = :origin")
  suspend fun childrenOf(origin: String): List<String>

  /**
   * Number of non deleted move edges arriving at [destination] (`destination` is indexed). Used by
   * the bounded descendant count to honour the convergence rule. See
   * [DatabaseQueryManager.countDescendants].
   */
  @Query("SELECT COUNT(*) FROM MoveEntity WHERE isDeleted = 0 AND destination = :destination")
  suspend fun incomingCount(destination: String): Int

  /** Whether a non deleted node row exists for [fen]. */
  @Query("SELECT EXISTS(SELECT 1 FROM NodeEntity WHERE isDeleted = 0 AND positionKey = :fen)")
  suspend fun nodeExists(fen: String): Boolean
}
