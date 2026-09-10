package proj.memorchess.axl.server.sync

import java.sql.Connection
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.time.Instant
import kotlin.uuid.Uuid
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

/** Largest number of nodes one user may own at once. Settings are exempt; see [SyncStore.push]. */
internal const val MAX_SYNC_NODES_PER_USER: Int = 20_000

/** Largest number of edges one user may own at once. */
internal const val MAX_SYNC_EDGES_PER_USER: Int = 20_000

/**
 * Largest number of local repertoires one user may own at once. Distinct from
 * [proj.memorchess.axl.server.repertoire.MAX_REPERTOIRES_PER_USER], which caps the published
 * catalog rather than a device's own repertoire list.
 */
internal const val MAX_SYNC_REPERTOIRES_PER_USER: Int = 200

/** Largest number of edge to repertoire tags one user may own at once. */
internal const val MAX_SYNC_TAGS_PER_USER: Int = 50_000

/**
 * The server side of the sync protocol, over a Postgres database.
 *
 * Every conflict goes through `:shared`'s `resolve`, and no SQL in here orders by `updated_at` to
 * decide a winner. That is deliberate: one implementation of the rule, shared with the client, is
 * the only way the two cannot drift apart.
 *
 * @param dataSource Pooled connections. Each call takes one and returns it.
 * @param maxNodesPerUser Cap on how many nodes one user may own.
 * @param maxEdgesPerUser Cap on how many edges one user may own.
 * @param maxRepertoiresPerUser Cap on how many local repertoires one user may own.
 * @param maxTagsPerUser Cap on how many edge to repertoire tags one user may own.
 * @param ioDispatcher Where the blocking JDBC work runs. Injected rather than hardcoded so a caller
 *   can substitute one, which also keeps the choice of dispatcher out of this class's business.
 */
internal class SyncStore(
  private val dataSource: DataSource,
  private val maxNodesPerUser: Int = MAX_SYNC_NODES_PER_USER,
  private val maxEdgesPerUser: Int = MAX_SYNC_EDGES_PER_USER,
  private val maxRepertoiresPerUser: Int = MAX_SYNC_REPERTOIRES_PER_USER,
  private val maxTagsPerUser: Int = MAX_SYNC_TAGS_PER_USER,
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
   * Nodes, edges, repertoires and tags are also checked against their per user cap before anything
   * is written: a push that would carry the owning user past one throws [QuotaExceededException]
   * and stores nothing. Settings carry no cap; they are small, bounded key/value pairs rather than
   * a resource a caller can grow without limit.
   *
   * @param serverNow The server's clock, passed in so the refusal boundary is testable.
   * @throws QuotaExceededException the batch would push some resource past its per user cap.
   */
  internal suspend fun push(
    userId: String,
    deviceId: String,
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
          deviceId,
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
   *
   * @throws QuotaExceededException nodes, edges or repertoires would push [userId] past its cap.
   *   Checked before anything is resolved or written, under [acquireUserLock], so two concurrent
   *   pushes from the same user can never both slip past it. Tags are checked further down, once
   *   the ones naming an edge the server has never seen are known and excluded from the count.
   * @throws UnknownDeviceException [deviceId] has no row under [userId].
   * @throws ResyncRequiredException [deviceId] was removed and has fallen below the garbage
   *   collection floor, so it may be holding rows whose tombstones are already gone.
   */
  private fun Connection.applyBatch(
    userId: String,
    deviceId: String,
    nodes: List<NodeSyncRow>,
    edges: List<EdgeSyncRow>,
    settings: List<SettingSyncRow>,
    repertoires: List<RepertoireSyncRow>,
    tags: List<EdgeRepertoireTagSyncRow>,
  ): Pair<Long, List<RejectedRow>> {
    acquireUserLock(userId)
    // Under the same lock as everything else, so a device removed concurrently cannot slip a batch
    // past the check.
    requireSyncableDevice(userId, deviceId)
    checkNodeQuota(userId, nodes, maxNodesPerUser)
    checkEdgeQuota(userId, edges, maxEdgesPerUser)
    checkRepertoireQuota(userId, repertoires, maxRepertoiresPerUser)

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
    checkTagQuota(userId, resolvableTags, maxTagsPerUser)

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
   * One bounded page of rows [deviceId] has not been served, ordered by the server assigned
   * revision.
   *
   * The position is a revision and **never** a timestamp. Using `updated_at` instead looks
   * equivalent and silently loses rows forever: a device with a slow clock writes a row stamped
   * earlier than a position another device has already passed, and that row is never returned again.
   *
   * Each resource is queried separately with its own limit, so a table that filled its page may
   * still be holding rows. [SyncPullResponse.nextCursor] is therefore the **lowest** such ceiling
   * across the five, and rows above it are withheld until the next page. Advancing further could
   * skip a row in a table that had not caught up, and re-sending is free because applying a row is
   * idempotent.
   *
   * Runs in one transaction under [acquireUserLock], so the five queries see one snapshot. Without
   * that, a push committing between two of them yields a page carrying an edge without the node
   * from the same push, and confirming it would authorize purging a tombstone the page never
   * carried.
   *
   * @param ack Token of the page the caller has just written locally, or `null` when it has
   *   committed nothing since its last failure. A token that matches nothing confirms nothing,
   *   which is what a replayed request looks like.
   * @param limit Maximum rows per resource; must be strictly positive.
   * @throws IllegalArgumentException when [limit] is not strictly positive.
   * @throws UnknownDeviceException when [deviceId] has no row under [userId].
   * @throws ResyncRequiredException when [deviceId] was removed and has fallen below the floor.
   */
  internal suspend fun pull(
    userId: String,
    deviceId: String,
    ack: String?,
    limit: Int,
    serverNow: Instant,
  ): SyncPullResponse {
    require(limit > 0) { "limit must be strictly positive, was $limit" }
    return inTransaction { connection ->
      connection.acquireUserLock(userId)
      val device = connection.requireSyncableDevice(userId, deviceId)

      // The token rotates on every response, so a replayed request holds one that no longer
      // matches, confirms nothing and is simply re served.
      val since =
        if (ack != null && ack == device.lastPageToken) {
          connection.setAcked(userId, deviceId, device.lastServed)
          device.lastServed
        } else {
          device.lastAcked
        }

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

      // A plain assignment. It states what this device was handed, which is what the column means.
      // A maximum would behave identically, since the ceiling cannot fall while the acknowledgement
      // is unchanged, but it would suggest a guarantee this column does not need.
      val servedThrough =
        listOf(nodes, edges, settings, repertoires, tags)
          .flatMap { page -> page.map { it.first } }
          .filter { ceiling == null || it <= ceiling }
          .maxOrNull() ?: since
      val pageToken = Uuid.random().toString()
      connection.setServed(userId, deviceId, servedThrough, pageToken)

      SyncPullResponse(
        serverTime = serverNow,
        nextCursor = ceiling,
        pageToken = pageToken,
        nodes = nodes.upTo(ceiling),
        edges = edges.upTo(ceiling),
        settings = settings.upTo(ceiling),
        repertoires = repertoires.upTo(ceiling),
        tags = tags.upTo(ceiling),
      )
    }
  }

  /**
   * The device's row, refusing a caller that may no longer sync.
   *
   * @throws UnknownDeviceException when there is no row: there is no position to serve from and no
   *   floor to check against, and serving from `0` would hand a full dataset to a device the
   *   watermark cannot see.
   */
  private fun Connection.requireSyncableDevice(userId: String, deviceId: String): DeviceRow {
    val device = readDevice(userId, deviceId) ?: throw UnknownDeviceException(deviceId)
    if (device.removedAt != null && device.lastAcked < gcFloor(userId)) {
      throw ResyncRequiredException(deviceId)
    }
    return device
  }

  private fun Connection.setAcked(userId: String, deviceId: String, acked: Long) {
    prepareStatement(
        "UPDATE sync_device SET last_acked_revision = ? WHERE user_id = ? AND device_id = ?"
      )
      .use { statement ->
        statement.setLong(1, acked)
        statement.setString(2, userId)
        statement.setString(3, deviceId)
        statement.executeUpdate()
      }
  }

  private fun Connection.setServed(
    userId: String,
    deviceId: String,
    served: Long,
    pageToken: String,
  ) {
    prepareStatement(
        "UPDATE sync_device SET last_served_revision = ?, last_page_token = ? " +
          "WHERE user_id = ? AND device_id = ?"
      )
      .use { statement ->
        statement.setLong(1, served)
        statement.setString(2, pageToken)
        statement.setString(3, userId)
        statement.setString(4, deviceId)
        statement.executeUpdate()
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
   * Upserts one device, refreshing its last seen time and its reported platform.
   *
   * @param afterReset The caller reports having wiped its synced local state, which is the only
   *   thing that lets a device below the garbage collection floor start over.
   * @return [RegisterOutcome.ResyncRequired] when this device was removed and has fallen below that
   *   floor, [RegisterOutcome.Ok] otherwise.
   */
  internal suspend fun registerDevice(
    userId: String,
    deviceId: String,
    platform: String,
    afterReset: Boolean,
    serverNow: Instant,
  ): RegisterOutcome =
    inTransaction { connection ->
      val existing = connection.readDevice(userId, deviceId)
      when {
        // First, and unconditionally: a 204 confirming a reset can be lost in transit, and the
        // client then retries against a row that is already reinstated. Ignoring the flag there
        // would leave a device that has just wiped its database sitting at its old acknowledgement,
        // silently missing everything below it.
        afterReset -> connection.reinstateAndZero(userId, deviceId, platform, serverNow)
        existing == null || existing.removedAt == null ->
          connection.upsertDevice(userId, deviceId, platform, serverNow)
        existing.lastAcked >= connection.gcFloor(userId) ->
          connection.reinstate(userId, deviceId, platform, serverNow)
        else -> return@inTransaction RegisterOutcome.ResyncRequired
      }
      RegisterOutcome.Ok
    }

  /** Clears the removal and starts the device over at nothing seen. */
  private fun Connection.reinstateAndZero(
    userId: String,
    deviceId: String,
    platform: String,
    serverNow: Instant,
  ) {
    upsertDevice(userId, deviceId, platform, serverNow)
    prepareStatement(
        "UPDATE sync_device SET removed_at = NULL, last_acked_revision = 0, " +
          "last_served_revision = 0, last_page_token = NULL " +
          "WHERE user_id = ? AND device_id = ?"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setString(2, deviceId)
        statement.executeUpdate()
      }
  }

  /** Clears the removal, keeping the position, for a device that is missing nothing. */
  private fun Connection.reinstate(
    userId: String,
    deviceId: String,
    platform: String,
    serverNow: Instant,
  ) {
    upsertDevice(userId, deviceId, platform, serverNow)
    prepareStatement("UPDATE sync_device SET removed_at = NULL WHERE user_id = ? AND device_id = ?")
      .use { statement ->
        statement.setString(1, userId)
        statement.setString(2, deviceId)
        statement.executeUpdate()
      }
  }

  /**
   * The highest revision below which a tombstone may already be gone for [userId].
   *
   * `0` when this user has never been collected, which makes "no floor at all" fall out of the same
   * comparison rather than needing a branch of its own.
   */
  private fun Connection.gcFloor(userId: String): Long =
    prepareStatement("SELECT floor_revision FROM sync_gc_floor WHERE user_id = ?").use { statement ->
      statement.setString(1, userId)
      statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0L }
    }

  private fun Connection.readDevice(userId: String, deviceId: String): DeviceRow? =
    readDevices(userId).firstOrNull { it.deviceId == deviceId }

  /** Marks a device removed, so the watermark stops waiting on it. Test only until the follow up. */
  internal suspend fun removeDeviceForTest(userId: String, deviceId: String, at: Instant) {
    inTransaction { connection ->
      connection
        .prepareStatement(
          "UPDATE sync_device SET removed_at = ? WHERE user_id = ? AND device_id = ?"
        )
        .use { statement ->
          statement.setTimestamp(1, at.toTimestamp())
          statement.setString(2, userId)
          statement.setString(3, deviceId)
          statement.executeUpdate()
        }
    }
  }

  /** Forces a user's garbage collection floor. Test only. */
  internal suspend fun setGcFloorForTest(userId: String, floor: Long) {
    inTransaction { connection -> connection.setGcFloor(userId, floor) }
  }

  private fun Connection.setGcFloor(userId: String, floor: Long) {
    prepareStatement(
        "INSERT INTO sync_gc_floor (user_id, floor_revision) VALUES (?, ?) " +
          "ON CONFLICT (user_id) DO UPDATE SET floor_revision = EXCLUDED.floor_revision"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setLong(2, floor)
        statement.executeUpdate()
      }
  }

  /** Inserts the row, or refreshes the platform and last seen time of the one already there. */
  private fun Connection.upsertDevice(
    userId: String,
    deviceId: String,
    platform: String,
    serverNow: Instant,
  ) {
    prepareStatement(
        "INSERT INTO sync_device (user_id, device_id, platform, last_seen_at) " +
          "VALUES (?, ?, ?, ?) ON CONFLICT (user_id, device_id) DO UPDATE SET " +
          "platform = EXCLUDED.platform, last_seen_at = EXCLUDED.last_seen_at"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.setString(2, deviceId)
        statement.setString(3, platform)
        statement.setTimestamp(4, serverNow.toTimestamp())
        statement.executeUpdate()
      }
  }

  /**
   * Whether [deviceId] has confirmed committing every row [userId] has, or `null` when there is no
   * live row for it.
   *
   * The comparison is `>=` rather than equality because collection can lower the user's maximum:
   * when the newest row is a tombstone every device has committed, collecting it drops that maximum
   * below the very acknowledgements that authorized the delete, and equality would then report a
   * fully caught up device as behind.
   */
  internal suspend fun deviceStatus(userId: String, deviceId: String): Boolean? =
    inTransaction { connection ->
      val device =
        connection.readDevice(userId, deviceId)?.takeIf { it.removedAt == null }
          ?: return@inTransaction null
      device.lastAcked >= connection.highestRevision(userId)
    }

  /**
   * The highest revision [userId] owns across [PER_USER_TABLES], or `0` when they own nothing.
   *
   * Per user, and never the `sync_revision` sequence itself: that counter is global, so comparing
   * against it would report every device as behind forever.
   */
  private fun Connection.highestRevision(userId: String): Long {
    val union =
      PER_USER_TABLES.joinToString(" UNION ALL ") {
        "SELECT max(revision) AS revision FROM $it WHERE user_id = ?"
      }
    return prepareStatement("SELECT COALESCE(max(revision), 0) FROM ($union) AS revisions").use {
      statement ->
      PER_USER_TABLES.forEachIndexed { index, _ -> statement.setString(index + 1, userId) }
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getLong(1)
      }
    }
  }

  /** Forces a device's position, so the floor boundaries are reachable without paging. Test only. */
  internal suspend fun setPositionForTest(
    userId: String,
    deviceId: String,
    lastAcked: Long,
    lastServed: Long,
  ) {
    inTransaction { connection ->
      connection
        .prepareStatement(
          "UPDATE sync_device SET last_acked_revision = ?, last_served_revision = ? " +
            "WHERE user_id = ? AND device_id = ?"
        )
        .use { statement ->
          statement.setLong(1, lastAcked)
          statement.setLong(2, lastServed)
          statement.setString(3, userId)
          statement.setString(4, deviceId)
          statement.executeUpdate()
        }
    }
  }

  /** Every device row [userId] owns, removed ones included. Test only. */
  internal suspend fun listDevicesForTest(userId: String): List<DeviceRow> =
    inTransaction { connection -> connection.readDevices(userId) }

  private fun Connection.readDevices(userId: String): List<DeviceRow> =
    prepareStatement(
        "SELECT device_id, platform, last_acked_revision, last_served_revision, " +
          "last_page_token, removed_at FROM sync_device WHERE user_id = ? ORDER BY device_id"
      )
      .use { statement ->
        statement.setString(1, userId)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                DeviceRow(
                  deviceId = rows.getString(1),
                  platform = rows.getString(2),
                  lastAcked = rows.getLong(3),
                  lastServed = rows.getLong(4),
                  lastPageToken = rows.getString(5),
                  removedAt = rows.getTimestamp(6)?.toInstant()?.toKotlinInstant(),
                )
              )
            }
          }
        }
      }

  /**
   * Deletes every tombstone every registered device has confirmed committing, per user.
   *
   * One transaction per user, never one spanning the loop: a single transaction over every user
   * would hold every user's advisory lock at once and block all pushes for as long as this ran.
   *
   * Idempotent, so two instances firing on the same schedule are safe. The second takes the same
   * per user lock and finds nothing left to delete.
   */
  internal suspend fun collectTombstones() {
    for (userId in usersWithDevices()) {
      val watermark = watermarkOf(userId) ?: continue
      // Either some device has committed nothing yet, or there is genuinely nothing to reclaim.
      if (watermark == 0L) continue
      inTransaction { connection ->
        connection.acquireUserLock(userId)
        for (table in PER_USER_TABLES) {
          connection
            .prepareStatement(
              "DELETE FROM $table WHERE user_id = ? AND is_deleted AND revision <= ?"
            )
            .use { statement ->
              statement.setString(1, userId)
              statement.setLong(2, watermark)
              statement.executeUpdate()
            }
        }
        connection.setGcFloor(userId, watermark)
      }
    }
  }

  /** Every user with at least one device row, which is the per user gate on collection. */
  private suspend fun usersWithDevices(): List<String> =
    inTransaction { connection ->
      connection.prepareStatement("SELECT DISTINCT user_id FROM sync_device").use { statement ->
        statement.executeQuery().use { rows ->
          buildList { while (rows.next()) add(rows.getString(1)) }
        }
      }
    }

  /**
   * The lowest acknowledgement across [userId]'s devices that are still registered, or `null` when
   * every one of them has been removed.
   *
   * Removed devices are excluded, which is the whole point of removal: one lost install would
   * otherwise hold this user's watermark down forever.
   */
  private suspend fun watermarkOf(userId: String): Long? =
    inTransaction { connection ->
      connection
        .prepareStatement(
          "SELECT min(last_acked_revision) FROM sync_device " +
            "WHERE user_id = ? AND removed_at IS NULL"
        )
        .use { statement ->
          statement.setString(1, userId)
          statement.executeQuery().use { rows ->
            rows.next()
            val value = rows.getLong(1)
            if (rows.wasNull()) null else value
          }
        }
    }

  /** The user's collection floor, or `null` when they have never been collected. Test only. */
  internal suspend fun gcFloorForTest(userId: String): Long? =
    inTransaction { connection ->
      connection.prepareStatement("SELECT floor_revision FROM sync_gc_floor WHERE user_id = ?").use {
        statement ->
        statement.setString(1, userId)
        statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
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
      for (table in PER_USER_TABLES + "sync_device" + "sync_gc_floor") {
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

  /**
   * Serializes every push from [userId] against each other for the rest of the transaction.
   *
   * Without it, two concurrent pushes from the same user could each check a quota against the same
   * stale count, both decide they are under the cap, and together push the user over it.
   */
  private fun Connection.acquireUserLock(userId: String) {
    prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").use { statement ->
      statement.setString(1, "sync-user:$userId")
      statement.executeQuery().use { it.next() }
    }
  }

  /** Rows [userId] already owns in [table]. [table] is always one of this file's own literals. */
  private fun Connection.countUserRows(table: String, userId: String): Int =
    prepareStatement("SELECT count(*) FROM $table WHERE user_id = ?").use { statement ->
      statement.setString(1, userId)
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getInt(1)
      }
    }

  /** @throws QuotaExceededException [incoming] would push [userId] past [cap] nodes. */
  private fun Connection.checkNodeQuota(
    userId: String,
    incoming: List<NodeSyncRow>,
    cap: Int,
  ) {
    val keys = incoming.map { it.positionKey }.distinct()
    if (keys.isEmpty()) return
    val existingAmongIncoming =
      prepareStatement(
          "SELECT count(*) FROM user_node n JOIN position p ON p.id = n.position_id " +
            "WHERE n.user_id = ? AND p.position_key = ANY (?)"
        )
        .use { statement ->
          statement.setString(1, userId)
          statement.setArray(2, createArrayOf("text", keys.toTypedArray()))
          statement.executeQuery().use { rows ->
            rows.next()
            rows.getInt(1)
          }
        }
    val projected = countUserRows("user_node", userId) + (keys.size - existingAmongIncoming)
    if (projected > cap) {
      throw QuotaExceededException("this push would use $projected of your $cap node quota")
    }
  }

  /** @throws QuotaExceededException [incoming] would push [userId] past [cap] edges. */
  private fun Connection.checkEdgeQuota(
    userId: String,
    incoming: List<EdgeSyncRow>,
    cap: Int,
  ) {
    val identities = incoming.map { it.origin to it.destination }.distinct()
    if (identities.isEmpty()) return
    var existingAmongIncoming = 0
    prepareStatement(
        "SELECT count(*) FROM user_edge ue " +
          "JOIN move_edge e ON e.id = ue.edge_id " +
          JOIN_EDGE_ENDPOINTS +
          "WHERE ue.user_id = ? AND po.position_key = ? AND pd.position_key = ?"
      )
      .use { statement ->
        for ((origin, destination) in identities) {
          statement.setString(1, userId)
          statement.setString(2, origin)
          statement.setString(3, destination)
          statement.executeQuery().use { rows ->
            rows.next()
            if (rows.getInt(1) > 0) existingAmongIncoming++
          }
        }
      }
    val projected = countUserRows("user_edge", userId) + (identities.size - existingAmongIncoming)
    if (projected > cap) {
      throw QuotaExceededException("this push would use $projected of your $cap edge quota")
    }
  }

  /** @throws QuotaExceededException [incoming] would push [userId] past [cap] repertoires. */
  private fun Connection.checkRepertoireQuota(
    userId: String,
    incoming: List<RepertoireSyncRow>,
    cap: Int,
  ) {
    val ids = incoming.map { it.id }.distinct()
    if (ids.isEmpty()) return
    val existingAmongIncoming =
      prepareStatement(
          "SELECT count(*) FROM user_repertoire WHERE user_id = ? AND repertoire_id = ANY (?)"
        )
        .use { statement ->
          statement.setString(1, userId)
          statement.setArray(2, createArrayOf("text", ids.toTypedArray()))
          statement.executeQuery().use { rows ->
            rows.next()
            rows.getInt(1)
          }
        }
    val projected = countUserRows("user_repertoire", userId) + (ids.size - existingAmongIncoming)
    if (projected > cap) {
      throw QuotaExceededException("this push would use $projected of your $cap repertoire quota")
    }
  }

  /** @throws QuotaExceededException [incoming] would push [userId] past [cap] tags. */
  private fun Connection.checkTagQuota(
    userId: String,
    incoming: List<EdgeRepertoireTagSyncRow>,
    cap: Int,
  ) {
    val identities = incoming.map { Triple(it.origin, it.destination, it.repertoireId) }.distinct()
    if (identities.isEmpty()) return
    var existingAmongIncoming = 0
    prepareStatement(
        "SELECT count(*) FROM user_edge_repertoire_tag t " +
          "JOIN move_edge e ON e.id = t.edge_id " +
          JOIN_EDGE_ENDPOINTS +
          "WHERE t.user_id = ? AND po.position_key = ? AND pd.position_key = ? " +
          "AND t.repertoire_id = ?"
      )
      .use { statement ->
        for ((origin, destination, repertoireId) in identities) {
          statement.setString(1, userId)
          statement.setString(2, origin)
          statement.setString(3, destination)
          statement.setString(4, repertoireId)
          statement.executeQuery().use { rows ->
            rows.next()
            if (rows.getInt(1) > 0) existingAmongIncoming++
          }
        }
      }
    val projected =
      countUserRows("user_edge_repertoire_tag", userId) + (identities.size - existingAmongIncoming)
    if (projected > cap) {
      throw QuotaExceededException("this push would use $projected of your $cap tag quota")
    }
  }
}

/**
 * Refused because applying the batch would push some resource past its per user cap. Nothing in the
 * batch is stored, request row locks or not.
 */
internal class QuotaExceededException(message: String) : Exception(message)

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

/** What [SyncStore.registerDevice] decided about a device that came back. */
internal sealed class RegisterOutcome {

  /** The device is registered and may sync. */
  data object Ok : RegisterOutcome()

  /** The device must wipe its synced local state and register again reporting the wipe. */
  data object ResyncRequired : RegisterOutcome()
}

/**
 * One `sync_device` row.
 *
 * @property lastAcked Highest revision this device confirmed writing locally.
 * @property lastServed Highest revision the server handed it.
 * @property lastPageToken Token issued with its most recent page, or `null` before its first pull.
 * @property removedAt When it was removed, or `null` while it is registered.
 */
internal data class DeviceRow(
  val deviceId: String,
  val platform: String,
  val lastAcked: Long,
  val lastServed: Long,
  val lastPageToken: String?,
  val removedAt: Instant?,
)

/** A caller named a device the server has never been told about. */
internal class UnknownDeviceException(deviceId: String) :
  Exception("device '$deviceId' is not registered")

/** A removed device came back below what garbage collection already purged. */
internal class ResyncRequiredException(deviceId: String) :
  Exception("device '$deviceId' must resync from scratch")
