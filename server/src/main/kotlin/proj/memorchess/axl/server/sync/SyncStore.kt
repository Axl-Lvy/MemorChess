package proj.memorchess.axl.server.sync

import java.sql.Connection
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RejectedRow
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.ResolutionSource
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncPushResponse
import proj.memorchess.axl.core.sync.isTooFarAhead
import proj.memorchess.axl.core.sync.resolve
import proj.memorchess.axl.server.db.EdgeIdentity
import proj.memorchess.axl.server.db.resolveEdgeIds
import proj.memorchess.axl.server.db.resolvePositionIds

/**
 * The server side of the sync protocol, over a Postgres database.
 *
 * Every conflict goes through `:shared`'s `resolve`, and no SQL in here orders by `updated_at` to
 * decide a winner. That is deliberate: one implementation of the rule, shared with the client, is
 * the only way the two cannot drift apart.
 *
 * @param dataSource Pooled connections. Each call takes one and returns it.
 */
internal class SyncStore(private val dataSource: DataSource) {

  /**
   * Applies a batch under last write wins and reports whatever was refused.
   *
   * A row is stored byte identical to the row that was sent, or refused; it is never rewritten. A
   * row whose `updatedAt` is further ahead than the tolerance allows comes back in
   * [SyncPushResponse.rejected] so the client can re-stamp and retry.
   *
   * The whole batch is one transaction, and rows are applied in key order so concurrent pushes take
   * locks in the same sequence.
   *
   * @param serverNow The server's clock, passed in so the refusal boundary is testable.
   */
  internal suspend fun push(
    userId: String,
    request: SyncPushRequest,
    serverNow: Instant,
  ): SyncPushResponse =
    withContext(Dispatchers.IO) {
      val rejected = mutableListOf<RejectedRow>()
      var highestRevision = 0L

      val acceptedNodes =
        request.nodes.filter { row ->
          val refuse = row.isTooFarAhead(serverNow)
          if (refuse) rejected += clockRefusal("node", row.positionKey)
          !refuse
        }
      val acceptedEdges =
        request.edges.filter { row ->
          val refuse = row.isTooFarAhead(serverNow)
          if (refuse) rejected += clockRefusal("edge", row.edgeId())
          !refuse
        }
      val acceptedSettings =
        request.settings.filter { row ->
          val refuse = row.isTooFarAhead(serverNow)
          if (refuse) rejected += clockRefusal("setting", row.key)
          !refuse
        }

      dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
          val positionIds = connection.resolvePositionIds(acceptedNodes.map { it.positionKey })
          val edgeIds =
            connection.resolveEdgeIds(
              acceptedEdges.map { EdgeIdentity(it.origin, it.destination, it.move) }
            )

          // Sorted within each resource so concurrent pushes take row locks in one order.
          for (row in acceptedNodes.sortedBy { it.positionKey }) {
            val revision = connection.applyNode(userId, row, positionIds.getValue(row.positionKey))
            if (revision != null) highestRevision = maxOf(highestRevision, revision)
          }
          for (row in acceptedEdges.sortedBy { it.edgeId() }) {
            val identity = EdgeIdentity(row.origin, row.destination, row.move)
            val revision = connection.applyEdge(userId, row, edgeIds.getValue(identity))
            if (revision != null) highestRevision = maxOf(highestRevision, revision)
          }
          for (row in acceptedSettings.sortedBy { it.key }) {
            val revision = connection.applySetting(userId, row)
            if (revision != null) highestRevision = maxOf(highestRevision, revision)
          }
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }

      SyncPushResponse(serverTime = serverNow, revision = highestRevision, rejected = rejected)
    }

  /** Reads one stored node. Exposed so the push tests do not depend on `pull` being correct. */
  internal suspend fun readNodeForTest(userId: String, positionKey: String): NodeSyncRow? =
    withContext(Dispatchers.IO) {
      dataSource.connection.use { connection ->
        val id = connection.resolvePositionIds(listOf(positionKey))[positionKey] ?: return@use null
        connection.readNode(userId, positionKey, id)
      }
    }

  /** Reads one stored edge. Exposed so the push tests do not depend on `pull` being correct. */
  internal suspend fun readEdgeForTest(userId: String, edge: EdgeSyncRow): EdgeSyncRow? =
    withContext(Dispatchers.IO) {
      dataSource.connection.use { connection ->
        val identity = EdgeIdentity(edge.origin, edge.destination, edge.move)
        val id = connection.resolveEdgeIds(listOf(identity))[identity] ?: return@use null
        connection.readEdge(userId, identity, id)
      }
    }

  /** See [applySetting]; the rule and the revision bump on a loss are identical. */
  private fun Connection.applyNode(
    userId: String,
    incoming: NodeSyncRow,
    positionId: Long,
  ): Long? {
    val stored = readNode(userId, incoming.positionKey, positionId, lockRow = true)
    val winner = resolve(local = stored, remote = incoming)
    if (winner.source == ResolutionSource.LOCAL && winner.row == incoming) return null

    val revision = nextRevision()
    val row = winner.row
    prepareStatement(
        "INSERT INTO user_node (user_id, position_id, due_date, last_review, first_review, " +
          "stability, difficulty, reps, lapses, phase, step, is_deleted, deleted_at, updated_at, " +
          "origin_device, device_seq, revision) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (user_id, position_id) DO UPDATE SET due_date = EXCLUDED.due_date, " +
          "last_review = EXCLUDED.last_review, first_review = EXCLUDED.first_review, " +
          "stability = EXCLUDED.stability, difficulty = EXCLUDED.difficulty, " +
          "reps = EXCLUDED.reps, lapses = EXCLUDED.lapses, phase = EXCLUDED.phase, " +
          "step = EXCLUDED.step, is_deleted = EXCLUDED.is_deleted, " +
          "deleted_at = EXCLUDED.deleted_at, updated_at = EXCLUDED.updated_at, " +
          "origin_device = EXCLUDED.origin_device, device_seq = EXCLUDED.device_seq, " +
          "revision = EXCLUDED.revision"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setLong(2, positionId)
        statement.setTimestamp(3, row.dueDate.toTimestamp())
        statement.setTimestamp(4, row.lastReview?.toTimestamp())
        statement.setTimestamp(5, row.firstReview?.toTimestamp())
        statement.setDouble(6, row.stability)
        statement.setDouble(7, row.difficulty)
        statement.setInt(8, row.reps)
        statement.setInt(9, row.lapses)
        statement.setString(10, row.phase)
        statement.setInt(11, row.step)
        statement.setBoolean(12, row.isDeleted)
        statement.setTimestamp(13, if (row.isDeleted) row.updatedAt.toTimestamp() else null)
        statement.setTimestamp(14, row.updatedAt.toTimestamp())
        statement.setString(15, row.originDevice)
        statement.setLong(16, row.deviceSeq)
        statement.setLong(17, revision)
        statement.executeUpdate()
      }
    return revision
  }

  private fun Connection.readNode(
    userId: String,
    positionKey: String,
    positionId: Long,
    lockRow: Boolean = false,
  ): NodeSyncRow? {
    val sql =
      "SELECT due_date, last_review, first_review, stability, difficulty, reps, lapses, phase, " +
        "step, is_deleted, updated_at, origin_device, device_seq FROM user_node " +
        "WHERE user_id = ? AND position_id = ?" + if (lockRow) " FOR UPDATE" else ""
    return prepareStatement(sql).use { statement ->
      statement.setString(1, userId)
      statement.setLong(2, positionId)
      statement.executeQuery().use { rows ->
        if (!rows.next()) null
        else
          NodeSyncRow(
            positionKey = positionKey,
            dueDate = rows.getTimestamp(1).toInstant().toKotlinInstant(),
            lastReview = rows.getTimestamp(2)?.toInstant()?.toKotlinInstant(),
            firstReview = rows.getTimestamp(3)?.toInstant()?.toKotlinInstant(),
            stability = rows.getDouble(4),
            difficulty = rows.getDouble(5),
            reps = rows.getInt(6),
            lapses = rows.getInt(7),
            phase = rows.getString(8),
            step = rows.getInt(9),
            isDeleted = rows.getBoolean(10),
            updatedAt = rows.getTimestamp(11).toInstant().toKotlinInstant(),
            originDevice = rows.getString(12),
            deviceSeq = rows.getLong(13),
          )
      }
    }
  }

  /** See [applySetting]; the rule and the revision bump on a loss are identical. */
  private fun Connection.applyEdge(userId: String, incoming: EdgeSyncRow, edgeId: Long): Long? {
    val identity = EdgeIdentity(incoming.origin, incoming.destination, incoming.move)
    val stored = readEdge(userId, identity, edgeId, lockRow = true)
    val winner = resolve(local = stored, remote = incoming)
    if (winner.source == ResolutionSource.LOCAL && winner.row == incoming) return null

    val revision = nextRevision()
    val row = winner.row
    prepareStatement(
        "INSERT INTO user_edge (user_id, edge_id, is_good, is_deleted, deleted_at, updated_at, " +
          "origin_device, device_seq, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (user_id, edge_id) DO UPDATE SET is_good = EXCLUDED.is_good, " +
          "is_deleted = EXCLUDED.is_deleted, deleted_at = EXCLUDED.deleted_at, " +
          "updated_at = EXCLUDED.updated_at, origin_device = EXCLUDED.origin_device, " +
          "device_seq = EXCLUDED.device_seq, revision = EXCLUDED.revision"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setLong(2, edgeId)
        statement.setBoolean(3, row.isGood)
        statement.setBoolean(4, row.isDeleted)
        statement.setTimestamp(5, if (row.isDeleted) row.updatedAt.toTimestamp() else null)
        statement.setTimestamp(6, row.updatedAt.toTimestamp())
        statement.setString(7, row.originDevice)
        statement.setLong(8, row.deviceSeq)
        statement.setLong(9, revision)
        statement.executeUpdate()
      }
    return revision
  }

  private fun Connection.readEdge(
    userId: String,
    identity: EdgeIdentity,
    edgeId: Long,
    lockRow: Boolean = false,
  ): EdgeSyncRow? {
    val sql =
      "SELECT is_good, is_deleted, updated_at, origin_device, device_seq FROM user_edge " +
        "WHERE user_id = ? AND edge_id = ?" + if (lockRow) " FOR UPDATE" else ""
    return prepareStatement(sql).use { statement ->
      statement.setString(1, userId)
      statement.setLong(2, edgeId)
      statement.executeQuery().use { rows ->
        if (!rows.next()) null
        else
          EdgeSyncRow(
            origin = identity.origin,
            destination = identity.destination,
            move = identity.move,
            isGood = rows.getBoolean(1),
            isDeleted = rows.getBoolean(2),
            updatedAt = rows.getTimestamp(3).toInstant().toKotlinInstant(),
            originDevice = rows.getString(4),
            deviceSeq = rows.getLong(5),
          )
      }
    }
  }

  /** Reads one stored setting. Exposed so the push tests do not depend on `pull` being correct. */
  internal suspend fun readSettingForTest(userId: String, key: String): SettingSyncRow? =
    withContext(Dispatchers.IO) { dataSource.connection.use { it.readSetting(userId, key) } }

  /**
   * Writes one setting under last write wins, returning the revision assigned, or `null` when the
   * incoming row was an identical replay and nothing needed announcing.
   *
   * When the incoming row **loses**, the surviving row's revision is advanced anyway. Without that,
   * the survivor sits at a revision the pusher's cursor has already passed, so the pusher never
   * receives it again and keeps a version everyone else rejected.
   */
  private fun Connection.applySetting(userId: String, incoming: SettingSyncRow): Long? {
    val stored = readSetting(userId, incoming.key, lockRow = true)
    val winner = resolve(local = stored, remote = incoming)
    if (winner.source == ResolutionSource.LOCAL && winner.row == incoming) return null

    val revision = nextRevision()
    val row = winner.row
    prepareStatement(
        "INSERT INTO user_setting (user_id, key, value, is_deleted, deleted_at, updated_at, " +
          "origin_device, device_seq, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (user_id, key) DO UPDATE SET value = EXCLUDED.value, " +
          "is_deleted = EXCLUDED.is_deleted, deleted_at = EXCLUDED.deleted_at, " +
          "updated_at = EXCLUDED.updated_at, origin_device = EXCLUDED.origin_device, " +
          "device_seq = EXCLUDED.device_seq, revision = EXCLUDED.revision"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setString(2, row.key)
        statement.setString(3, row.value)
        statement.setBoolean(4, row.isDeleted)
        statement.setTimestamp(5, if (row.isDeleted) row.updatedAt.toTimestamp() else null)
        statement.setTimestamp(6, row.updatedAt.toTimestamp())
        statement.setString(7, row.originDevice)
        statement.setLong(8, row.deviceSeq)
        statement.setLong(9, revision)
        statement.executeUpdate()
      }
    return revision
  }

  private fun Connection.readSetting(
    userId: String,
    key: String,
    lockRow: Boolean = false,
  ): SettingSyncRow? {
    // FOR UPDATE matters: without the row lock two concurrent pushes for one key both read the old
    // row, both decide they win, and one silently overwrites the other's decision.
    val sql =
      "SELECT value, is_deleted, updated_at, origin_device, device_seq FROM user_setting " +
        "WHERE user_id = ? AND key = ?" + if (lockRow) " FOR UPDATE" else ""
    return prepareStatement(sql).use { statement ->
      statement.setString(1, userId)
      statement.setString(2, key)
      statement.executeQuery().use { rows ->
        if (!rows.next()) null
        else
          SettingSyncRow(
            key = key,
            value = rows.getString(1),
            isDeleted = rows.getBoolean(2),
            updatedAt = rows.getTimestamp(3).toInstant().toKotlinInstant(),
            originDevice = rows.getString(4),
            deviceSeq = rows.getLong(5),
          )
      }
    }
  }

  private fun Connection.nextRevision(): Long =
    prepareStatement("SELECT nextval('sync_revision')").use { statement ->
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getLong(1)
      }
    }
}

/** Stable identifier for an edge in a rejection report, matching what the client can compute. */
private fun EdgeSyncRow.edgeId(): String = "$origin|$destination"

private fun clockRefusal(kind: String, id: String) =
  RejectedRow(
    kind = kind,
    id = id,
    code = RejectionCode.CLOCK_TOO_FAR_AHEAD,
    reason = "updatedAt is further ahead of server time than the tolerance allows",
  )

/**
 * Converts without losing precision.
 *
 * Going through `toEpochMilliseconds()` would truncate: `kotlin.time.Instant` carries nanoseconds
 * and `timestamptz` carries microseconds, so a millisecond round trip would silently move a
 * timestamp and a stored row would no longer be byte identical to the row that was sent.
 */
private fun Instant.toTimestamp(): Timestamp =
  Timestamp.from(java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()))

private fun java.time.Instant.toKotlinInstant(): Instant =
  Instant.fromEpochSeconds(epochSecond, nano.toLong())
