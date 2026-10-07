package proj.memorchess.axl.server.sync

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import proj.memorchess.axl.core.sync.DevicePlatform
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
    checkQuota(userId, NODE_QUOTA, nodes, maxNodesPerUser)
    checkQuota(userId, EDGE_QUOTA, edges, maxEdgesPerUser)
    checkQuota(userId, REPERTOIRE_QUOTA, repertoires, maxRepertoiresPerUser)

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
    checkQuota(userId, TAG_QUOTA, resolvableTags, maxTagsPerUser)

    val revisions =
      applyAll(userId, NODES, nodes.sortedBy { it.positionKey }) {
        positionIds.getValue(it.positionKey)
      } +
        applyAll(userId, EDGES, edges.sortedBy { it.edgeId() }) {
          allEdgeIds.getValue(it.origin to it.destination)
        } +
        applyAll(userId, SETTINGS, settings.sortedBy { it.key }) { it.key } +
        applyAll(userId, REPERTOIRES, repertoires.sortedBy { it.id }) { it.id } +
        applyAll(
          userId,
          TAGS,
          resolvableTags.sortedBy { "${it.origin}|${it.destination}|${it.repertoireId}" },
        ) {
          allEdgeIds.getValue(it.origin to it.destination) to it.repertoireId
        }
    val revision = revisions.maxOrNull() ?: 0L
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
        "SELECT po.position_key, pd.position_key, e.id FROM move_edge e " +
          JOIN_EDGE_ENDPOINTS +
          "JOIN unnest(?::text[], ?::text[]) AS k(o, d) " +
          "ON po.position_key = k.o AND pd.position_key = k.d"
      )
      .use { statement ->
        statement.setArray(1, createArrayOf("text", endpoints.map { it.first }.toTypedArray()))
        statement.setArray(2, createArrayOf("text", endpoints.map { it.second }.toTypedArray()))
        statement.executeQuery().use { rows ->
          while (rows.next()) ids[rows.getString(1) to rows.getString(2)] = rows.getLong(3)
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
   * earlier than a position another device has already passed, and that row is never returned
   * again.
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
    platform: DevicePlatform,
    afterReset: Boolean,
    serverNow: Instant,
  ): RegisterOutcome = inTransaction { connection ->
    // Under the same lock as collection, which reads this device's position and the floor this
    // decision is made against. Without it a removed device can be reinstated keeping a position
    // that a concurrently written floor is about to overtake, and it would then never be told to
    // resync while its tombstones were purged underneath it.
    connection.acquireUserLock(userId)
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
    platform: DevicePlatform,
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
    platform: DevicePlatform,
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
    prepareStatement("SELECT floor_revision FROM sync_gc_floor WHERE user_id = ?").use { statement
      ->
      statement.setString(1, userId)
      statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0L }
    }

  private fun Connection.readDevice(userId: String, deviceId: String): DeviceRow? =
    readDevices(userId).firstOrNull { it.deviceId == deviceId }

  /**
   * Marks a device removed, so the watermark stops waiting on it. Test only until the follow up.
   */
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
    platform: DevicePlatform,
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
        // The column is text, so the enum becomes its wire name here and nowhere else.
        statement.setString(3, platform.wireName)
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

  /**
   * Forces a device's position, so the floor boundaries are reachable without paging. Test only.
   */
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
    inTransaction { connection ->
      connection.readDevices(userId)
    }

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
                  platform =
                    rows.getString(2).let(DevicePlatform::fromWire)
                      ?: error("stored platform '${rows.getString(2)}' is not a known one"),
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
      try {
        collectTombstonesOf(userId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // One user's failure must not cost every later user in this run its collection, and the
        // scheduled job must survive to run again tomorrow.
        logger.error("Tombstone collection failed for user '{}', continuing", userId, e)
      }
    }
  }

  /** Collects one user's tombstones, in a single transaction under their lock. */
  private suspend fun collectTombstonesOf(userId: String) {
    inTransaction { connection ->
      connection.acquireUserLock(userId)
      // Read under the lock that guards the delete below, never before it. A registration
      // landing between an unlocked read and this block could reinstate a removed device at a
      // position the floor written here is about to overtake.
      val watermark = connection.watermarkOf(userId)
      // Either some device has committed nothing yet, or there is genuinely nothing to reclaim.
      if (watermark == null || watermark == 0L) return@inTransaction
      for (table in PER_USER_TABLES) {
        connection
          .prepareStatement("DELETE FROM $table WHERE user_id = ? AND is_deleted AND revision <= ?")
          .use { statement ->
            statement.setString(1, userId)
            statement.setLong(2, watermark)
            statement.executeUpdate()
          }
      }
      connection.setGcFloor(userId, watermark)
    }
  }

  /** Every user with at least one device row, which is the per user gate on collection. */
  private suspend fun usersWithDevices(): List<String> = inTransaction { connection ->
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
  private fun Connection.watermarkOf(userId: String): Long? =
    prepareStatement(
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

  /** The user's collection floor, or `null` when they have never been collected. Test only. */
  internal suspend fun gcFloorForTest(userId: String): Long? = inTransaction { connection ->
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

  /**
   * Writes [rows] under last write wins and returns the revisions assigned, skipping identical
   * replays.
   *
   * Reads every stored counterpart in one locked query, resolves in memory, then writes the winners
   * in one batch, so the number of round trips does not grow with [rows]. A row repeating an
   * earlier key is resolved against that earlier winner. A losing row still advances the survivor's
   * revision, so the pusher is served the survivor again.
   */
  private fun <R : SyncRow, K> Connection.applyAll(
    userId: String,
    table: SyncTable<R, K>,
    rows: List<R>,
    keyOf: (R) -> K,
  ): List<Long> {
    if (rows.isEmpty()) return emptyList()
    val keyed = rows.map { keyOf(it) to it }
    val current = readAllLocked(userId, table, keyed).toMutableMap()
    val decided = ArrayList<Pair<K, R>>()
    for ((key, incoming) in keyed) {
      val winner = resolve(local = current[key], remote = incoming)
      if (winner.source == ResolutionSource.LOCAL && winner.row == incoming) continue
      current[key] = winner.row
      decided += key to winner.row
    }
    if (decided.isEmpty()) return emptyList()
    val revisions = nextRevisions(decided.size)
    val lastPerKey = LinkedHashMap<K, Pair<R, Long>>()
    decided.forEachIndexed { index, (key, row) -> lastPerKey[key] = row to revisions[index] }
    upsertAll(userId, table, lastPerKey)
    return revisions
  }

  /**
   * The stored rows of every key in [keyed], locked for the rest of the transaction, by key. A key
   * with no stored row is absent.
   */
  private fun <R : SyncRow, K> Connection.readAllLocked(
    userId: String,
    table: SyncTable<R, K>,
    keyed: List<Pair<K, R>>,
  ): Map<K, R> {
    val incomingByKey = HashMap<K, R>(keyed.size)
    for ((key, row) in keyed) incomingByKey.putIfAbsent(key, row)
    val keys = incomingByKey.keys.toList()
    val stored = HashMap<K, R>(keys.size)
    prepareStatement(table.selectSql).use { statement ->
      table.keyTypes.forEachIndexed { column, type ->
        statement.setArray(
          column + 1,
          createArrayOf(type, keys.map { table.keyParts(it)[column] }.toTypedArray()),
        )
      }
      statement.setString(table.keyTypes.size + 1, userId)
      statement.executeQuery().use { rows ->
        val versionStart = table.payloadColumns.size + 1
        val keyStart = versionStart + STORED_VERSION_COLUMNS.size
        while (rows.next()) {
          val key = table.readKey(rows, keyStart)
          val version =
            StoredVersion(
              isDeleted = rows.getBoolean(versionStart),
              updatedAt = rows.getTimestamp(versionStart + 1).toInstant().toKotlinInstant(),
              originDevice = rows.getString(versionStart + 2),
              deviceSeq = rows.getLong(versionStart + 3),
            )
          stored[key] = table.readPayload(rows, incomingByKey.getValue(key), version)
        }
      }
    }
    return stored
  }

  /** Inserts or overwrites every row in [writes] under its key, each stamped with its revision. */
  private fun <R : SyncRow, K> Connection.upsertAll(
    userId: String,
    table: SyncTable<R, K>,
    writes: Map<K, Pair<R, Long>>,
  ) {
    prepareStatement(table.upsertSql).use { statement ->
      for ((key, versioned) in writes) {
        val (row, revision) = versioned
        statement.setString(1, userId)
        table.keyParts(key).forEachIndexed { index, part -> statement.setObject(2 + index, part) }
        val first = 2 + table.keyColumns.size
        table.bindPayload(statement, first, row)
        val tail = first + table.payloadColumns.size
        statement.setBoolean(tail, row.isDeleted)
        statement.setTimestamp(tail + 1, if (row.isDeleted) row.updatedAt.toTimestamp() else null)
        statement.setTimestamp(tail + 2, row.updatedAt.toTimestamp())
        statement.setString(tail + 3, row.originDevice)
        statement.setLong(tail + 4, row.deviceSeq)
        statement.setLong(tail + 5, revision)
        statement.addBatch()
      }
      statement.executeBatch()
    }
  }

  /** [count] fresh revisions, ascending. */
  private fun Connection.nextRevisions(count: Int): List<Long> =
    prepareStatement("SELECT nextval('sync_revision') FROM generate_series(1, ?)").use { statement
      ->
      statement.setInt(1, count)
      statement.executeQuery().use { rows ->
        buildList { while (rows.next()) add(rows.getLong(1)) }.sorted()
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

  /** @throws QuotaExceededException [incoming] would push [userId] past [cap] rows of [rule]. */
  private fun <R, I> Connection.checkQuota(
    userId: String,
    rule: QuotaRule<R, I>,
    incoming: List<R>,
    cap: Int,
  ) {
    val identities = incoming.map(rule.identity).distinct()
    if (identities.isEmpty()) return
    val existingAmongIncoming = rule.countStored(this, userId, identities)
    val projected = countUserRows(rule.table, userId) + (identities.size - existingAmongIncoming)
    if (projected > cap) {
      throw QuotaExceededException("this push would use $projected of your $cap ${rule.noun} quota")
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

/** Columns every last write wins table stores after its payload, in bind order. */
private val VERSION_COLUMNS =
  listOf("is_deleted", "deleted_at", "updated_at", "origin_device", "device_seq", "revision")

/** Columns every last write wins table is read back from after its payload, in read order. */
private val STORED_VERSION_COLUMNS =
  listOf("is_deleted", "updated_at", "origin_device", "device_seq")

/** The versioning half of a stored row, which every [SyncRow] type carries alike. */
private class StoredVersion(
  val isDeleted: Boolean,
  val updatedAt: Instant,
  val originDevice: String,
  val deviceSeq: Long,
)

/**
 * How one per user table stores rows of type [R] under last write wins.
 *
 * @param K What identifies a row within the table once shared `position` and `move_edge` ids are
 *   resolved.
 * @property keyColumns Columns that, beside `user_id`, identify one row, in [keyParts] order.
 * @property payloadColumns Columns holding the row's own data, in [bindPayload] and [readPayload]
 *   order.
 * @property keyTypes The SQL type of each of [keyColumns], so a batch of keys binds as arrays.
 * @property keyParts Splits a key into one value per [keyColumns] entry.
 * @property readKey Rebuilds a key from [keyColumns], read from the given result set index.
 * @property bindPayload Binds a row's data to [payloadColumns], starting at the given parameter
 *   index.
 * @property readPayload Rebuilds the stored row from [payloadColumns], read from index `1`, taking
 *   its identity from the incoming row it is being resolved against.
 */
private class SyncTable<R : SyncRow, K>(
  val table: String,
  val keyColumns: List<String>,
  val payloadColumns: List<String>,
  val keyTypes: List<String>,
  val keyParts: (K) -> List<Any>,
  val readKey: (ResultSet, Int) -> K,
  val bindPayload: (PreparedStatement, Int, R) -> Unit,
  val readPayload: (ResultSet, R, StoredVersion) -> R,
) {

  /**
   * Selects every row of one `user_id` whose key is among the given arrays, one per [keyColumns]
   * entry, locking them. Reads payload, then version, then key columns.
   */
  val selectSql: String =
    "SELECT ${(payloadColumns + STORED_VERSION_COLUMNS + keyColumns).joinToString { "t.$it" }} " +
      "FROM $table t " +
      "JOIN unnest(${keyTypes.joinToString { "?::$it[]" }}) AS k(${keyColumns.joinToString()}) " +
      "ON ${keyColumns.joinToString(" AND ") { "t.$it = k.$it" }} " +
      "WHERE t.user_id = ?" +
      FOR_UPDATE +
      " OF t"

  /** Inserts one row, or overwrites every non key column of the row already there. */
  val upsertSql: String =
    (listOf("user_id") + keyColumns + payloadColumns + VERSION_COLUMNS).let { columns ->
      "INSERT INTO $table (${columns.joinToString()}) " +
        "VALUES (${columns.joinToString { "?" }}) " +
        "ON CONFLICT (${(listOf("user_id") + keyColumns).joinToString()}) DO UPDATE SET " +
        (payloadColumns + VERSION_COLUMNS).joinToString { "$it = EXCLUDED.$it" }
    }
}

/** Nodes, keyed by their interned `position` id. */
private val NODES =
  SyncTable<NodeSyncRow, Long>(
    table = "user_node",
    keyColumns = listOf("position_id"),
    payloadColumns =
      listOf(
        "due_date",
        "last_review",
        "first_review",
        "stability",
        "difficulty",
        "reps",
        "lapses",
        "phase",
        "step",
      ),
    keyTypes = listOf("bigint"),
    keyParts = { listOf(it) },
    readKey = { rows, index -> rows.getLong(index) },
    bindPayload = { statement, first, row ->
      statement.setTimestamp(first, row.dueDate.toTimestamp())
      statement.setTimestamp(first + 1, row.lastReview?.toTimestamp())
      statement.setTimestamp(first + 2, row.firstReview?.toTimestamp())
      statement.setDouble(first + 3, row.stability)
      statement.setDouble(first + 4, row.difficulty)
      statement.setInt(first + 5, row.reps)
      statement.setInt(first + 6, row.lapses)
      statement.setString(first + 7, row.phase)
      statement.setInt(first + 8, row.step)
    },
    readPayload = { rows, incoming, version ->
      NodeSyncRow(
        positionKey = incoming.positionKey,
        dueDate = rows.getTimestamp(1).toInstant().toKotlinInstant(),
        lastReview = rows.getTimestamp(2)?.toInstant()?.toKotlinInstant(),
        firstReview = rows.getTimestamp(3)?.toInstant()?.toKotlinInstant(),
        stability = rows.getDouble(4),
        difficulty = rows.getDouble(5),
        reps = rows.getInt(6),
        lapses = rows.getInt(7),
        phase = rows.getString(8),
        step = rows.getInt(9),
        isDeleted = version.isDeleted,
        updatedAt = version.updatedAt,
        originDevice = version.originDevice,
        deviceSeq = version.deviceSeq,
      )
    },
  )

/** Edges, keyed by their interned `move_edge` id. */
private val EDGES =
  SyncTable<EdgeSyncRow, Long>(
    table = "user_edge",
    keyColumns = listOf("edge_id"),
    payloadColumns = listOf("is_good"),
    keyTypes = listOf("bigint"),
    keyParts = { listOf(it) },
    readKey = { rows, index -> rows.getLong(index) },
    bindPayload = { statement, first, row -> statement.setBoolean(first, row.isGood) },
    readPayload = { rows, incoming, version ->
      EdgeSyncRow(
        origin = incoming.origin,
        destination = incoming.destination,
        move = incoming.move,
        isGood = rows.getBoolean(1),
        isDeleted = version.isDeleted,
        updatedAt = version.updatedAt,
        originDevice = version.originDevice,
        deviceSeq = version.deviceSeq,
      )
    },
  )

/** Settings, keyed by their own key. */
private val SETTINGS =
  SyncTable<SettingSyncRow, String>(
    table = "user_setting",
    keyColumns = listOf("key"),
    payloadColumns = listOf("value"),
    keyTypes = listOf("text"),
    keyParts = { listOf(it) },
    readKey = { rows, index -> rows.getString(index) },
    bindPayload = { statement, first, row -> statement.setString(first, row.value) },
    readPayload = { rows, incoming, version ->
      SettingSyncRow(
        key = incoming.key,
        value = rows.getString(1),
        isDeleted = version.isDeleted,
        updatedAt = version.updatedAt,
        originDevice = version.originDevice,
        deviceSeq = version.deviceSeq,
      )
    },
  )

/** Local repertoires, keyed by their own id. */
private val REPERTOIRES =
  SyncTable<RepertoireSyncRow, String>(
    table = "user_repertoire",
    keyColumns = listOf("repertoire_id"),
    payloadColumns = listOf("name", "color"),
    keyTypes = listOf("text"),
    keyParts = { listOf(it) },
    readKey = { rows, index -> rows.getString(index) },
    bindPayload = { statement, first, row ->
      statement.setString(first, row.name)
      statement.setString(first + 1, row.color)
    },
    readPayload = { rows, incoming, version ->
      RepertoireSyncRow(
        id = incoming.id,
        name = rows.getString(1),
        color = rows.getString(2),
        isDeleted = version.isDeleted,
        updatedAt = version.updatedAt,
        originDevice = version.originDevice,
        deviceSeq = version.deviceSeq,
      )
    },
  )

/** Edge to repertoire tags, keyed by the interned `move_edge` id and the repertoire id. */
private val TAGS =
  SyncTable<EdgeRepertoireTagSyncRow, Pair<Long, String>>(
    table = "user_edge_repertoire_tag",
    keyColumns = listOf("edge_id", "repertoire_id"),
    payloadColumns = emptyList(),
    keyTypes = listOf("bigint", "text"),
    keyParts = { (edgeId, repertoireId) -> listOf(edgeId, repertoireId) },
    readKey = { rows, index -> rows.getLong(index) to rows.getString(index + 1) },
    bindPayload = { _, _, _ -> },
    readPayload = { _, incoming, version ->
      EdgeRepertoireTagSyncRow(
        origin = incoming.origin,
        destination = incoming.destination,
        repertoireId = incoming.repertoireId,
        isDeleted = version.isDeleted,
        updatedAt = version.updatedAt,
        originDevice = version.originDevice,
        deviceSeq = version.deviceSeq,
      )
    },
  )

/**
 * One per user row cap, counted over the wire identities of the incoming rows.
 *
 * @property noun What the refusal message calls one row.
 * @property identity What makes two incoming rows the same stored row.
 * @property countStored How many of the given distinct identities [table] already holds for a user.
 */
private class QuotaRule<R, I>(
  val noun: String,
  val table: String,
  val identity: (R) -> I,
  val countStored: (Connection, String, List<I>) -> Int,
)

private val NODE_QUOTA =
  QuotaRule<NodeSyncRow, String>(
    noun = "node",
    table = NODES.table,
    identity = { it.positionKey },
    countStored =
      countStoredAmong(
        "SELECT count(*) FROM user_node n JOIN position p ON p.id = n.position_id " +
          "WHERE n.user_id = ? AND p.position_key = ANY (?)"
      ),
  )

private val EDGE_QUOTA =
  QuotaRule<EdgeSyncRow, Pair<String, String>>(
    noun = "edge",
    table = EDGES.table,
    identity = { it.origin to it.destination },
    countStored =
      countStoredAmongTuples(
        "SELECT count(*) FROM user_edge ue " +
          "JOIN move_edge e ON e.id = ue.edge_id " +
          JOIN_EDGE_ENDPOINTS +
          "JOIN unnest(?::text[], ?::text[]) AS k(o, d) " +
          "ON po.position_key = k.o AND pd.position_key = k.d " +
          "WHERE ue.user_id = ?"
      ) { (origin, destination) ->
        listOf(origin, destination)
      },
  )

private val REPERTOIRE_QUOTA =
  QuotaRule<RepertoireSyncRow, String>(
    noun = "repertoire",
    table = REPERTOIRES.table,
    identity = { it.id },
    countStored =
      countStoredAmong(
        "SELECT count(*) FROM user_repertoire WHERE user_id = ? AND repertoire_id = ANY (?)"
      ),
  )

private val TAG_QUOTA =
  QuotaRule<EdgeRepertoireTagSyncRow, Triple<String, String, String>>(
    noun = "tag",
    table = TAGS.table,
    identity = { Triple(it.origin, it.destination, it.repertoireId) },
    countStored =
      countStoredAmongTuples(
        "SELECT count(*) FROM user_edge_repertoire_tag t " +
          "JOIN move_edge e ON e.id = t.edge_id " +
          JOIN_EDGE_ENDPOINTS +
          "JOIN unnest(?::text[], ?::text[], ?::text[]) AS k(o, d, r) " +
          "ON po.position_key = k.o AND pd.position_key = k.d AND t.repertoire_id = k.r " +
          "WHERE t.user_id = ?"
      ) { (origin, destination, repertoireId) ->
        listOf(origin, destination, repertoireId)
      },
  )

/** Counts in one query, [sql] taking the user id then the text keys as an array. */
private fun countStoredAmong(sql: String): (Connection, String, List<String>) -> Int =
  { connection, userId, keys ->
    connection.prepareStatement(sql).use { statement ->
      statement.setString(1, userId)
      statement.setArray(2, connection.createArrayOf("text", keys.toTypedArray()))
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getInt(1)
      }
    }
  }

/**
 * Counts in one query, [sql] taking one text array per entry of [partsOf], then the user id.
 *
 * Counts rows rather than identities, so it relies on the identities being distinct.
 */
private fun <I> countStoredAmongTuples(
  sql: String,
  partsOf: (I) -> List<String>,
): (Connection, String, List<I>) -> Int = { connection, userId, identities ->
  val parts = identities.map(partsOf)
  connection.prepareStatement(sql).use { statement ->
    val width = parts.first().size
    for (column in 0 until width) {
      statement.setArray(
        column + 1,
        connection.createArrayOf("text", parts.map { it[column] }.toTypedArray()),
      )
    }
    statement.setString(width + 1, userId)
    statement.executeQuery().use { rows ->
      rows.next()
      rows.getInt(1)
    }
  }
}

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
  val platform: DevicePlatform,
  val lastAcked: Long,
  val lastServed: Long,
  val lastPageToken: String?,
  val removedAt: Instant?,
)

private val logger = LoggerFactory.getLogger(SyncStore::class.java)

/** A caller named a device the server has never been told about. */
internal class UnknownDeviceException(deviceId: String) :
  Exception("device '$deviceId' is not registered")

/** A removed device came back below what garbage collection already purged. */
internal class ResyncRequiredException(deviceId: String) :
  Exception("device '$deviceId' must resync from scratch")
