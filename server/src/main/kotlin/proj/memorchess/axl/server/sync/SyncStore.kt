package proj.memorchess.axl.server.sync

import java.sql.Connection
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import proj.memorchess.axl.core.sync.EdgeRepertoireTagSyncRow
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RejectedRow
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.RepertoireSyncRow
import proj.memorchess.axl.core.sync.ResolutionSource
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPullResponse
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncPushResponse
import proj.memorchess.axl.core.sync.SyncRow
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
 * @param ioDispatcher Where the blocking JDBC work runs. Injected rather than hardcoded so a caller
 *   can substitute one, which also keeps the choice of dispatcher out of this class's business.
 */
internal class SyncStore(
  private val dataSource: DataSource,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

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
  ): SyncPushResponse {
    val nodes = request.nodes.screenClock(serverNow) { clockRefusal("node", it.positionKey) }
    val edges = request.edges.screenClock(serverNow) { clockRefusal("edge", it.edgeId()) }
    val settings = request.settings.screenClock(serverNow) { clockRefusal("setting", it.key) }
    val repertoires =
      request.repertoires.screenClock(serverNow) { clockRefusal("repertoire", it.id) }
    val tags =
      request.tags.screenClock(serverNow) {
        clockRefusal("tag", "${it.origin}|${it.destination}|${it.repertoireId}")
      }

    val (revision, orphanedTags) =
      inTransaction { connection ->
        connection.applyBatch(
          userId,
          nodes.accepted,
          edges.accepted,
          settings.accepted,
          repertoires.accepted,
          tags.accepted,
        )
      }

    return SyncPushResponse(
      serverTime = serverNow,
      revision = revision,
      rejected =
        nodes.refused +
          edges.refused +
          settings.refused +
          repertoires.refused +
          tags.refused +
          orphanedTags,
    )
  }

  /**
   * Applies every accepted row in one transaction and returns the highest revision assigned (`0`
   * when nothing needed writing), plus the [RejectedRow]s for any tag naming an edge the server has
   * no record of.
   *
   * Rows are sorted within each resource so that concurrent pushes take row locks in one order.
   */
  private fun Connection.applyBatch(
    userId: String,
    nodes: List<NodeSyncRow>,
    edges: List<EdgeSyncRow>,
    settings: List<SettingSyncRow>,
    repertoires: List<RepertoireSyncRow>,
    tags: List<EdgeRepertoireTagSyncRow>,
  ): Pair<Long, List<RejectedRow>> {
    val positionIds = resolvePositionIds(nodes.map { it.positionKey })
    val edgeIds =
      resolveEdgeIds(edges.map { it.identity() }).mapKeys { (identity, _) ->
        identity.origin to identity.destination
      }
    // A tag may reference an edge not present in this same batch's `edges` list (it was pushed
    // earlier). Its endpoints are looked up, never created: an edge is interned only by pushing it
    // as an EdgeSyncRow, so a tag naming one the server has never seen is refused rather than
    // silently minting a move_edge row with no real move, which move_edge's write-once move column
    // could never correct afterward.
    val unresolvedTagEndpoints = tags.map { it.origin to it.destination }.toSet() - edgeIds.keys
    val lookedUpEdgeIds = lookupEdgeIds(unresolvedTagEndpoints)
    val allEdgeIds = edgeIds + lookedUpEdgeIds
    val (resolvableTags, orphanedTags) =
      tags.partition { (it.origin to it.destination) in allEdgeIds }

    val revisions = buildList {
      nodes
        .sortedBy { it.positionKey }
        .forEach { add(applyNode(userId, it, positionIds.getValue(it.positionKey))) }
      edges
        .sortedBy { it.edgeId() }
        .forEach { add(applyEdge(userId, it, allEdgeIds.getValue(it.origin to it.destination))) }
      settings.sortedBy { it.key }.forEach { add(applySetting(userId, it)) }
      repertoires.sortedBy { it.id }.forEach { add(applyRepertoire(userId, it)) }
      resolvableTags
        .sortedBy { "${it.origin}|${it.destination}|${it.repertoireId}" }
        .forEach { add(applyTag(userId, it, allEdgeIds.getValue(it.origin to it.destination))) }
    }
    val revision = revisions.filterNotNull().maxOrNull() ?: 0L
    return revision to
      orphanedTags.map {
        RejectedRow(
          kind = "tag",
          id = "${it.origin}|${it.destination}|${it.repertoireId}",
          code = RejectionCode.EDGE_NOT_FOUND,
          reason = "no known edge from ${it.origin} to ${it.destination}",
        )
      }
  }

  /**
   * Looks up existing `move_edge` ids for [endpoints], creating nothing: unlike [resolveEdgeIds],
   * an endpoint pair the server has never seen resolves to no entry rather than a freshly minted
   * row.
   */
  private fun Connection.lookupEdgeIds(
    endpoints: Set<Pair<String, String>>
  ): Map<Pair<String, String>, Long> {
    if (endpoints.isEmpty()) return emptyMap()
    val ids = HashMap<Pair<String, String>, Long>(endpoints.size)
    prepareStatement(
        "SELECT e.id FROM move_edge e " +
          JOIN_EDGE_ENDPOINTS +
          "WHERE po.position_key = ? AND pd.position_key = ?"
      )
      .use { statement ->
        for ((origin, destination) in endpoints) {
          statement.setString(1, origin)
          statement.setString(2, destination)
          statement.executeQuery().use { rows ->
            if (rows.next()) ids[origin to destination] = rows.getLong(1)
          }
        }
      }
    return ids
  }

  /**
   * Runs [block] in one transaction on a pooled connection, committing on success and rolling back
   * on any failure.
   */
  private suspend fun <T> inTransaction(block: (Connection) -> T): T =
    withContext(ioDispatcher) {
      dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
          block(connection).also { connection.commit() }
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
    }

  /**
   * One bounded page of rows the caller has not seen, ordered by the server assigned revision.
   *
   * The cursor is a revision and **never** a timestamp. Using `updated_at` instead looks equivalent
   * and silently loses rows forever: a device with a slow clock writes a row stamped earlier than a
   * cursor another device has already passed, and that row is never returned again.
   *
   * Each resource is queried separately with its own limit, so a table that filled its page may
   * still be holding rows. [SyncPullResponse.nextCursor] is therefore the **lowest** such ceiling
   * across the three, and rows above it are withheld until the next page. Advancing further could
   * skip a row in a table that had not caught up, and re-sending is free because applying a row is
   * idempotent. A `null` cursor means every table returned a partial page and the caller is up to
   * date.
   *
   * @param limit Maximum rows per resource; must be strictly positive.
   * @throws IllegalArgumentException when [limit] is not strictly positive.
   */
  internal suspend fun pull(
    userId: String,
    since: Long,
    limit: Int,
    serverNow: Instant,
  ): SyncPullResponse =
    withContext(ioDispatcher) {
      require(limit > 0) { "limit must be strictly positive, was $limit" }

      dataSource.connection.use { connection ->
        val nodes = connection.pullNodes(userId, since, limit)
        val edges = connection.pullEdges(userId, since, limit)
        val settings = connection.pullSettings(userId, since, limit)
        val repertoires = connection.pullRepertoires(userId, since, limit)
        val tags = connection.pullTags(userId, since, limit)

        // A page that came back full may be hiding more rows, so its last revision is a ceiling.
        // A partial page is exhausted and imposes none.
        val ceiling =
          listOf(nodes, edges, settings, repertoires, tags)
            .mapNotNull { page -> page.takeIf { it.size == limit }?.last()?.first }
            .minOrNull()

        fun <T> List<Pair<Long, T>>.upTo(bound: Long?) =
          (if (bound == null) this else filter { it.first <= bound }).map { it.second }

        SyncPullResponse(
          serverTime = serverNow,
          nextCursor = ceiling,
          nodes = nodes.upTo(ceiling),
          edges = edges.upTo(ceiling),
          settings = settings.upTo(ceiling),
          repertoires = repertoires.upTo(ceiling),
          tags = tags.upTo(ceiling),
        )
      }
    }

  private fun Connection.pullNodes(
    userId: String,
    since: Long,
    limit: Int,
  ): List<Pair<Long, NodeSyncRow>> =
    prepareStatement(
        "SELECT p.position_key, n.due_date, n.last_review, n.first_review, n.stability, " +
          "n.difficulty, n.reps, n.lapses, n.phase, n.step, n.is_deleted, n.updated_at, " +
          "n.origin_device, n.device_seq, n.revision FROM user_node n " +
          "JOIN position p ON p.id = n.position_id " +
          "WHERE n.user_id = ? AND n.revision > ? ORDER BY n.revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setLong(2, since)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                rows.getLong(15) to
                  NodeSyncRow(
                    positionKey = rows.getString(1),
                    dueDate = rows.getTimestamp(2).toInstant().toKotlinInstant(),
                    lastReview = rows.getTimestamp(3)?.toInstant()?.toKotlinInstant(),
                    firstReview = rows.getTimestamp(4)?.toInstant()?.toKotlinInstant(),
                    stability = rows.getDouble(5),
                    difficulty = rows.getDouble(6),
                    reps = rows.getInt(7),
                    lapses = rows.getInt(8),
                    phase = rows.getString(9),
                    step = rows.getInt(10),
                    isDeleted = rows.getBoolean(11),
                    updatedAt = rows.getTimestamp(12).toInstant().toKotlinInstant(),
                    originDevice = rows.getString(13),
                    deviceSeq = rows.getLong(14),
                  )
              )
            }
          }
        }
      }

  private fun Connection.pullEdges(
    userId: String,
    since: Long,
    limit: Int,
  ): List<Pair<Long, EdgeSyncRow>> =
    prepareStatement(
        "SELECT po.position_key, pd.position_key, e.move, ue.is_good, ue.is_deleted, " +
          "ue.updated_at, ue.origin_device, ue.device_seq, ue.revision FROM user_edge ue " +
          "JOIN move_edge e ON e.id = ue.edge_id " +
          JOIN_EDGE_ENDPOINTS +
          "WHERE ue.user_id = ? AND ue.revision > ? ORDER BY ue.revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setLong(2, since)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                rows.getLong(9) to
                  EdgeSyncRow(
                    origin = rows.getString(1),
                    destination = rows.getString(2),
                    move = rows.getString(3),
                    isGood = rows.getBoolean(4),
                    isDeleted = rows.getBoolean(5),
                    updatedAt = rows.getTimestamp(6).toInstant().toKotlinInstant(),
                    originDevice = rows.getString(7),
                    deviceSeq = rows.getLong(8),
                  )
              )
            }
          }
        }
      }

  private fun Connection.pullSettings(
    userId: String,
    since: Long,
    limit: Int,
  ): List<Pair<Long, SettingSyncRow>> =
    prepareStatement(
        "SELECT key, value, is_deleted, updated_at, origin_device, device_seq, revision " +
          "FROM user_setting WHERE user_id = ? AND revision > ? ORDER BY revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setLong(2, since)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                rows.getLong(7) to
                  SettingSyncRow(
                    key = rows.getString(1),
                    value = rows.getString(2),
                    isDeleted = rows.getBoolean(3),
                    updatedAt = rows.getTimestamp(4).toInstant().toKotlinInstant(),
                    originDevice = rows.getString(5),
                    deviceSeq = rows.getLong(6),
                  )
              )
            }
          }
        }
      }

  /**
   * Removes every row belonging to [userId].
   *
   * Only the three per user tables. The shared `position` and `move_edge` rows stay, because they
   * are append only and other users reference them.
   *
   * This is one half of account deletion. The identity itself lives with the auth provider and has
   * to be removed there too; performing only one half leaves a resurrectable account.
   */
  internal suspend fun deleteUser(userId: String) {
    inTransaction { connection ->
      for (table in PER_USER_TABLES) {
        connection.prepareStatement("DELETE FROM $table WHERE user_id = ?").use { statement ->
          statement.setString(1, userId)
          statement.executeUpdate()
        }
      }
    }
  }

  /** Reads one stored node. Exposed so the push tests do not depend on `pull` being correct. */
  internal suspend fun readNodeForTest(userId: String, positionKey: String): NodeSyncRow? =
    withContext(ioDispatcher) {
      dataSource.connection.use { connection ->
        val id = connection.resolvePositionIds(listOf(positionKey))[positionKey] ?: return@use null
        connection.readNode(userId, positionKey, id)
      }
    }

  /** Reads one stored edge. Exposed so the push tests do not depend on `pull` being correct. */
  internal suspend fun readEdgeForTest(userId: String, edge: EdgeSyncRow): EdgeSyncRow? =
    withContext(ioDispatcher) {
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
        "WHERE user_id = ? AND position_id = ?" +
        if (lockRow) FOR_UPDATE else ""
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
          LAST_WRITE_WINS_UPDATE_SET
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
        "WHERE user_id = ? AND edge_id = ?" +
        if (lockRow) FOR_UPDATE else ""
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
    withContext(ioDispatcher) { dataSource.connection.use { it.readSetting(userId, key) } }

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
          LAST_WRITE_WINS_UPDATE_SET
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
        "WHERE user_id = ? AND key = ?" +
        if (lockRow) FOR_UPDATE else ""
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

  /**
   * Reads one stored repertoire. Exposed so the push tests do not depend on `pull` being correct.
   */
  internal suspend fun readRepertoireForTest(userId: String, id: String): RepertoireSyncRow? =
    withContext(ioDispatcher) { dataSource.connection.use { it.readRepertoire(userId, id) } }

  /** See [applySetting]; the rule and the revision bump on a loss are identical. */
  private fun Connection.applyRepertoire(userId: String, incoming: RepertoireSyncRow): Long? {
    val stored = readRepertoire(userId, incoming.id, lockRow = true)
    val winner = resolve(local = stored, remote = incoming)
    if (winner.source == ResolutionSource.LOCAL && winner.row == incoming) return null

    val revision = nextRevision()
    val row = winner.row
    prepareStatement(
        "INSERT INTO user_repertoire (user_id, repertoire_id, name, color, is_deleted, " +
          "deleted_at, updated_at, origin_device, device_seq, revision) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (user_id, repertoire_id) DO UPDATE SET name = EXCLUDED.name, " +
          "color = EXCLUDED.color, is_deleted = EXCLUDED.is_deleted, " +
          "deleted_at = EXCLUDED.deleted_at, updated_at = EXCLUDED.updated_at, " +
          "origin_device = EXCLUDED.origin_device, device_seq = EXCLUDED.device_seq, " +
          "revision = EXCLUDED.revision"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setString(2, row.id)
        statement.setString(3, row.name)
        statement.setString(4, row.color)
        statement.setBoolean(5, row.isDeleted)
        statement.setTimestamp(6, if (row.isDeleted) row.updatedAt.toTimestamp() else null)
        statement.setTimestamp(7, row.updatedAt.toTimestamp())
        statement.setString(8, row.originDevice)
        statement.setLong(9, row.deviceSeq)
        statement.setLong(10, revision)
        statement.executeUpdate()
      }
    return revision
  }

  private fun Connection.readRepertoire(
    userId: String,
    id: String,
    lockRow: Boolean = false,
  ): RepertoireSyncRow? {
    val sql =
      "SELECT name, color, is_deleted, updated_at, origin_device, device_seq FROM user_repertoire " +
        "WHERE user_id = ? AND repertoire_id = ?" +
        if (lockRow) FOR_UPDATE else ""
    return prepareStatement(sql).use { statement ->
      statement.setString(1, userId)
      statement.setString(2, id)
      statement.executeQuery().use { rows ->
        if (!rows.next()) null
        else
          RepertoireSyncRow(
            id = id,
            name = rows.getString(1),
            color = rows.getString(2),
            isDeleted = rows.getBoolean(3),
            updatedAt = rows.getTimestamp(4).toInstant().toKotlinInstant(),
            originDevice = rows.getString(5),
            deviceSeq = rows.getLong(6),
          )
      }
    }
  }

  private fun Connection.pullRepertoires(
    userId: String,
    since: Long,
    limit: Int,
  ): List<Pair<Long, RepertoireSyncRow>> =
    prepareStatement(
        "SELECT repertoire_id, name, color, is_deleted, updated_at, origin_device, device_seq, " +
          "revision FROM user_repertoire WHERE user_id = ? AND revision > ? " +
          "ORDER BY revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setLong(2, since)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                rows.getLong(8) to
                  RepertoireSyncRow(
                    id = rows.getString(1),
                    name = rows.getString(2),
                    color = rows.getString(3),
                    isDeleted = rows.getBoolean(4),
                    updatedAt = rows.getTimestamp(5).toInstant().toKotlinInstant(),
                    originDevice = rows.getString(6),
                    deviceSeq = rows.getLong(7),
                  )
              )
            }
          }
        }
      }

  /** Reads one stored tag. Exposed so the push tests do not depend on `pull` being correct. */
  internal suspend fun readTagForTest(
    userId: String,
    tag: EdgeRepertoireTagSyncRow,
  ): EdgeRepertoireTagSyncRow? =
    withContext(ioDispatcher) {
      dataSource.connection.use { connection ->
        val identity = EdgeIdentity(tag.origin, tag.destination, "")
        val edgeId = connection.resolveEdgeIds(listOf(identity))[identity] ?: return@use null
        connection.readTag(userId, tag.origin, tag.destination, edgeId, tag.repertoireId)
      }
    }

  /** See [applySetting]; the rule and the revision bump on a loss are identical. */
  private fun Connection.applyTag(
    userId: String,
    incoming: EdgeRepertoireTagSyncRow,
    edgeId: Long,
  ): Long? {
    val stored =
      readTag(
        userId,
        incoming.origin,
        incoming.destination,
        edgeId,
        incoming.repertoireId,
        lockRow = true,
      )
    val winner = resolve(local = stored, remote = incoming)
    if (winner.source == ResolutionSource.LOCAL && winner.row == incoming) return null

    val revision = nextRevision()
    val row = winner.row
    prepareStatement(
        "INSERT INTO user_edge_repertoire_tag (user_id, edge_id, repertoire_id, is_deleted, " +
          "deleted_at, updated_at, origin_device, device_seq, revision) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (user_id, edge_id, repertoire_id) DO UPDATE SET " +
          LAST_WRITE_WINS_UPDATE_SET
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setLong(2, edgeId)
        statement.setString(3, row.repertoireId)
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

  private fun Connection.readTag(
    userId: String,
    origin: String,
    destination: String,
    edgeId: Long,
    repertoireId: String,
    lockRow: Boolean = false,
  ): EdgeRepertoireTagSyncRow? {
    val sql =
      "SELECT is_deleted, updated_at, origin_device, device_seq FROM user_edge_repertoire_tag " +
        "WHERE user_id = ? AND edge_id = ? AND repertoire_id = ?" +
        if (lockRow) FOR_UPDATE else ""
    return prepareStatement(sql).use { statement ->
      statement.setString(1, userId)
      statement.setLong(2, edgeId)
      statement.setString(3, repertoireId)
      statement.executeQuery().use { rows ->
        if (!rows.next()) null
        else
          EdgeRepertoireTagSyncRow(
            origin = origin,
            destination = destination,
            repertoireId = repertoireId,
            isDeleted = rows.getBoolean(1),
            updatedAt = rows.getTimestamp(2).toInstant().toKotlinInstant(),
            originDevice = rows.getString(3),
            deviceSeq = rows.getLong(4),
          )
      }
    }
  }

  private fun Connection.pullTags(
    userId: String,
    since: Long,
    limit: Int,
  ): List<Pair<Long, EdgeRepertoireTagSyncRow>> =
    prepareStatement(
        "SELECT po.position_key, pd.position_key, t.repertoire_id, t.is_deleted, t.updated_at, " +
          "t.origin_device, t.device_seq, t.revision FROM user_edge_repertoire_tag t " +
          "JOIN move_edge e ON e.id = t.edge_id " +
          JOIN_EDGE_ENDPOINTS +
          "WHERE t.user_id = ? AND t.revision > ? ORDER BY t.revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setLong(2, since)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                rows.getLong(8) to
                  EdgeRepertoireTagSyncRow(
                    origin = rows.getString(1),
                    destination = rows.getString(2),
                    repertoireId = rows.getString(3),
                    isDeleted = rows.getBoolean(4),
                    updatedAt = rows.getTimestamp(5).toInstant().toKotlinInstant(),
                    originDevice = rows.getString(6),
                    deviceSeq = rows.getLong(7),
                  )
              )
            }
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

/**
 * Locks the selected row for the rest of the transaction.
 *
 * Without it two concurrent pushes for one key both read the old row, both decide they win, and one
 * silently overwrites the other's decision.
 */
private const val FOR_UPDATE = " FOR UPDATE"

/** Joins a `move_edge` row to the `position` rows at both its ends, keyed `po`/`pd`. */
private const val JOIN_EDGE_ENDPOINTS =
  "JOIN position po ON po.id = e.origin_id JOIN position pd ON pd.id = e.destination_id "

/** The `ON CONFLICT ... DO UPDATE SET` tail shared by every last write wins upsert. */
private const val LAST_WRITE_WINS_UPDATE_SET =
  "is_deleted = EXCLUDED.is_deleted, deleted_at = EXCLUDED.deleted_at, " +
    "updated_at = EXCLUDED.updated_at, origin_device = EXCLUDED.origin_device, " +
    "device_seq = EXCLUDED.device_seq, revision = EXCLUDED.revision"

/** The tables holding per user rows. The shared `position` and `move_edge` are not among them. */
private val PER_USER_TABLES =
  listOf("user_node", "user_edge", "user_setting", "user_repertoire", "user_edge_repertoire_tag")

/** Screening outcome for one resource: what may be written, and what was refused. */
private class Screened<T : SyncRow>(val accepted: List<T>, val refused: List<RejectedRow>)

/**
 * Splits rows into those the server may store and those whose clock is too far ahead to accept.
 *
 * A pure split rather than a filter with a side effect, so neither half depends on iteration order.
 */
private fun <T : SyncRow> List<T>.screenClock(
  serverNow: Instant,
  refusal: (T) -> RejectedRow,
): Screened<T> {
  val (tooFarAhead, acceptable) = partition { it.isTooFarAhead(serverNow) }
  return Screened(acceptable, tooFarAhead.map(refusal))
}

/** Stable identifier for an edge in a rejection report, matching what the client can compute. */
private fun EdgeSyncRow.edgeId(): String = "$origin|$destination"

/** The shared identity this edge interns to. */
private fun EdgeSyncRow.identity() = EdgeIdentity(origin, destination, move)

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
