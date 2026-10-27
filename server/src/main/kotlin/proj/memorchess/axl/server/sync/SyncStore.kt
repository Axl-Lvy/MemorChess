package proj.memorchess.axl.server.sync

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import javax.sql.DataSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.sync.DeviceId
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.EdgeRepertoireTagSyncRow
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RejectedRow
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.RepertoireSyncRow
import proj.memorchess.axl.core.sync.ResolutionSource
import proj.memorchess.axl.core.sync.Revision
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPullResponse
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncPushResponse
import proj.memorchess.axl.core.sync.SyncRow
import proj.memorchess.axl.core.sync.isTooFarAhead
import proj.memorchess.axl.core.sync.resolve
import proj.memorchess.axl.server.auth.UserId
import proj.memorchess.axl.server.db.EdgeIdentity
import proj.memorchess.axl.server.db.resolveEdgeIds
import proj.memorchess.axl.server.db.resolvePositionIds

/** Largest number of nodes one user may own at once. */
internal const val MAX_SYNC_NODES_PER_USER: Int = 20_000

/** Largest number of edges one user may own at once. */
internal const val MAX_SYNC_EDGES_PER_USER: Int = 20_000

/** Largest number of local repertoires one user may own at once, published ones aside. */
internal const val MAX_SYNC_REPERTOIRES_PER_USER: Int = 200

/** Largest number of edge to repertoire tags one user may own at once. */
internal const val MAX_SYNC_TAGS_PER_USER: Int = 50_000

/**
 * Server side of the sync protocol, over Postgres. Every conflict goes through `:shared`'s
 * `resolve`, never an SQL order on `updated_at`, so client and server cannot drift apart.
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
   * Applies a batch under last write wins in one transaction, refusing rows stamped too far ahead.
   * After an erase, rows the cutoff covers are stored as [DeviceId.ERASE_ORIGIN] tombstones; see
   * [EraseCutoff.cuts].
   *
   * @throws QuotaExceededException the batch would push nodes, edges, repertoires or tags past
   *   their per user cap; nothing is stored.
   */
  internal suspend fun push(
    userId: UserId,
    deviceId: DeviceId,
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

    val revision = inTransaction { connection ->
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
        nodes.refused + edges.refused + settings.refused + repertoires.refused + tags.refused,
    )
  }

  /**
   * Applies every accepted row and returns the highest revision assigned, or [Revision.ZERO] when
   * nothing was written. Rows are sorted so concurrent pushes take row locks in one order.
   *
   * @throws QuotaExceededException some resource would push [userId] past its cap. Checked under
   *   [acquireUserLock] so two concurrent pushes from the same user cannot both slip past it.
   * @throws UnknownDeviceException [deviceId] has no row under [userId].
   * @throws ResyncRequiredException [deviceId] was removed and fell below the garbage collection
   *   floor.
   */
  private fun Connection.applyBatch(
    userId: UserId,
    deviceId: DeviceId,
    sentNodes: List<NodeSyncRow>,
    sentEdges: List<EdgeSyncRow>,
    settings: List<SettingSyncRow>,
    sentRepertoires: List<RepertoireSyncRow>,
    sentTags: List<EdgeRepertoireTagSyncRow>,
  ): Revision {
    acquireUserLock(userId)
    // Under the same lock as everything else, so a device removed concurrently cannot slip a batch
    // past the check.
    val device = requireSyncableDevice(userId, deviceId)
    // A device that has pulled the erase's tombstones writes after it whatever its clock says.
    val cutoff = eraseCutoffOf(userId)?.takeIf { device.lastAcked < it.erasedRevision }
    val nodes = if (cutoff == null) sentNodes else sentNodes.map { it.cutOffAt(cutoff) }
    val edges = if (cutoff == null) sentEdges else sentEdges.map { it.cutOffAt(cutoff) }
    val repertoires =
      if (cutoff == null) sentRepertoires else sentRepertoires.map { it.cutOffAt(cutoff) }
    val tags = if (cutoff == null) sentTags else sentTags.map { it.cutOffAt(cutoff) }
    checkQuota(userId, NODE_QUOTA, nodes, maxNodesPerUser)
    checkQuota(userId, EDGE_QUOTA, edges, maxEdgesPerUser)
    checkQuota(userId, REPERTOIRE_QUOTA, repertoires, maxRepertoiresPerUser)
    checkQuota(userId, TAG_QUOTA, tags, maxTagsPerUser)

    val positionIds = resolvePositionIds(nodes.map { it.positionKey })
    // A tag carries its move, so it interns its edge like an edge row does: an install pushes tags
    // for moves the user never holds manually.
    val allEdgeIds =
      resolveEdgeIds(edges.map { it.identity() } + tags.map { it.identity() }).mapKeys {
        (identity, _) ->
        identity.origin to identity.destination
      }

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
          tags.sortedBy { "${it.origin}|${it.destination}|${it.repertoireId}" },
        ) {
          allEdgeIds.getValue(it.origin to it.destination) to it.repertoireId
        }
    return revisions.maxOrNull() ?: Revision.ZERO
  }

  /** Runs [block] in one transaction on a pooled connection, rolling back on any failure. */
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
   * One bounded page of rows [deviceId] has not been served, ordered by server revision. The cursor
   * is a revision, never `updated_at`, which would lose rows written by a device with a slow clock.
   * [SyncPullResponse.nextCursor] is the lowest ceiling among full per resource pages, so no table
   * is skipped. Runs under [acquireUserLock] so all five queries see one snapshot.
   *
   * @param ack Token of the page the caller has just written locally, or `null` when it has
   *   committed nothing since its last failure.
   * @param limit Maximum rows per resource; must be strictly positive.
   * @throws UnknownDeviceException when [deviceId] has no row under [userId].
   * @throws ResyncRequiredException when [deviceId] was removed and has fallen below the floor.
   */
  internal suspend fun pull(
    userId: UserId,
    deviceId: DeviceId,
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

      fun <T> List<Pair<Revision, T>>.upTo(bound: Revision?) =
        (if (bound == null) this else filter { it.first <= bound }).map { it.second }

      // A plain assignment, not a maximum: the column states what this device was handed.
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
   * @throws UnknownDeviceException when there is no row, since serving from `0` would hand a full
   *   dataset to a device the watermark cannot see.
   */
  private fun Connection.requireSyncableDevice(userId: UserId, deviceId: DeviceId): DeviceRow {
    val device = readDevice(userId, deviceId) ?: throw UnknownDeviceException(deviceId)
    if (device.removedAt != null && device.lastAcked < gcFloor(userId)) {
      throw ResyncRequiredException(deviceId)
    }
    return device
  }

  private fun Connection.setAcked(userId: UserId, deviceId: DeviceId, acked: Revision) {
    prepareStatement(
        "UPDATE sync_device SET last_acked_revision = ? WHERE user_id = ? AND device_id = ?"
      )
      .use { statement ->
        statement.setLong(1, acked.value)
        statement.setString(2, userId.value)
        statement.setString(3, deviceId.value)
        statement.executeUpdate()
      }
  }

  private fun Connection.setServed(
    userId: UserId,
    deviceId: DeviceId,
    served: Revision,
    pageToken: String,
  ) {
    prepareStatement(
        "UPDATE sync_device SET last_served_revision = ?, last_page_token = ? " +
          "WHERE user_id = ? AND device_id = ?"
      )
      .use { statement ->
        statement.setLong(1, served.value)
        statement.setString(2, pageToken)
        statement.setString(3, userId.value)
        statement.setString(4, deviceId.value)
        statement.executeUpdate()
      }
  }

  private fun Connection.pullNodes(
    userId: UserId,
    since: Revision,
    limit: Int,
  ): List<Pair<Revision, NodeSyncRow>> =
    prepareStatement(
        "SELECT p.position_key, n.due_date, n.last_review, n.first_review, n.stability, " +
          "n.difficulty, n.reps, n.lapses, n.phase, n.step, n.is_deleted, n.updated_at, " +
          "n.origin_device, n.device_seq, n.revision FROM user_node n " +
          "JOIN position p ON p.id = n.position_id " +
          "WHERE n.user_id = ? AND n.revision > ? ORDER BY n.revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.setLong(2, since.value)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                Revision(rows.getLong(15)) to
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
                    originDevice = DeviceId(rows.getString(13)),
                    deviceSeq = rows.getLong(14),
                  )
              )
            }
          }
        }
      }

  private fun Connection.pullEdges(
    userId: UserId,
    since: Revision,
    limit: Int,
  ): List<Pair<Revision, EdgeSyncRow>> =
    prepareStatement(
        "SELECT po.position_key, pd.position_key, e.move, ue.is_good, ue.is_deleted, " +
          "ue.updated_at, ue.origin_device, ue.device_seq, ue.revision FROM user_edge ue " +
          "JOIN move_edge e ON e.id = ue.edge_id " +
          JOIN_EDGE_ENDPOINTS +
          "WHERE ue.user_id = ? AND ue.revision > ? ORDER BY ue.revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.setLong(2, since.value)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                Revision(rows.getLong(9)) to
                  EdgeSyncRow(
                    origin = rows.getString(1),
                    destination = rows.getString(2),
                    move = rows.getString(3),
                    isGood = rows.getBoolean(4),
                    isDeleted = rows.getBoolean(5),
                    updatedAt = rows.getTimestamp(6).toInstant().toKotlinInstant(),
                    originDevice = DeviceId(rows.getString(7)),
                    deviceSeq = rows.getLong(8),
                  )
              )
            }
          }
        }
      }

  private fun Connection.pullSettings(
    userId: UserId,
    since: Revision,
    limit: Int,
  ): List<Pair<Revision, SettingSyncRow>> =
    prepareStatement(
        "SELECT key, value, is_deleted, updated_at, origin_device, device_seq, revision " +
          "FROM user_setting WHERE user_id = ? AND revision > ? ORDER BY revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.setLong(2, since.value)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                Revision(rows.getLong(7)) to
                  SettingSyncRow(
                    key = rows.getString(1),
                    value = rows.getString(2),
                    isDeleted = rows.getBoolean(3),
                    updatedAt = rows.getTimestamp(4).toInstant().toKotlinInstant(),
                    originDevice = DeviceId(rows.getString(5)),
                    deviceSeq = rows.getLong(6),
                  )
              )
            }
          }
        }
      }

  /**
   * Upserts one device, refreshing its last seen time and platform, or returns
   * [RegisterOutcome.ResyncRequired] when it was removed and fell below the garbage collection
   * floor.
   *
   * @param afterReset The caller wiped its synced local state, the only way a device below the
   *   floor can start over.
   */
  internal suspend fun registerDevice(
    userId: UserId,
    deviceId: DeviceId,
    platform: DevicePlatform,
    afterReset: Boolean,
    serverNow: Instant,
  ): RegisterOutcome = inTransaction { connection ->
    // Under the collection lock, or a removed device could be reinstated at a position a concurrent
    // floor is about to overtake, and never be told to resync.
    connection.acquireUserLock(userId)
    val existing = connection.readDevice(userId, deviceId)
    when {
      // First and unconditionally: a lost 204 makes the client retry the reset against an already
      // reinstated row, which must still be zeroed.
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
    userId: UserId,
    deviceId: DeviceId,
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
        statement.setString(1, userId.value)
        statement.setString(2, deviceId.value)
        statement.executeUpdate()
      }
  }

  /** Clears the removal, keeping the position, for a device that is missing nothing. */
  private fun Connection.reinstate(
    userId: UserId,
    deviceId: DeviceId,
    platform: DevicePlatform,
    serverNow: Instant,
  ) {
    upsertDevice(userId, deviceId, platform, serverNow)
    prepareStatement("UPDATE sync_device SET removed_at = NULL WHERE user_id = ? AND device_id = ?")
      .use { statement ->
        statement.setString(1, userId.value)
        statement.setString(2, deviceId.value)
        statement.executeUpdate()
      }
  }

  /**
   * The revision below which a tombstone of [userId] may be gone, or [Revision.ZERO] when never
   * collected.
   */
  private fun Connection.gcFloor(userId: UserId): Revision =
    prepareStatement("SELECT floor_revision FROM sync_gc_floor WHERE user_id = ?").use { statement
      ->
      statement.setString(1, userId.value)
      statement.executeQuery().use { rows ->
        if (rows.next()) Revision(rows.getLong(1)) else Revision.ZERO
      }
    }

  private fun Connection.readDevice(userId: UserId, deviceId: DeviceId): DeviceRow? =
    readDevices(userId).firstOrNull { it.deviceId == deviceId }

  /** Marks a device removed, so the watermark stops waiting on it. Test only. */
  internal suspend fun removeDeviceForTest(userId: UserId, deviceId: DeviceId, at: Instant) {
    inTransaction { connection ->
      connection
        .prepareStatement(
          "UPDATE sync_device SET removed_at = ? WHERE user_id = ? AND device_id = ?"
        )
        .use { statement ->
          statement.setTimestamp(1, at.toTimestamp())
          statement.setString(2, userId.value)
          statement.setString(3, deviceId.value)
          statement.executeUpdate()
        }
    }
  }

  /** Forces a user's garbage collection floor. Test only. */
  internal suspend fun setGcFloorForTest(userId: UserId, floor: Revision) {
    inTransaction { connection -> connection.setGcFloor(userId, floor) }
  }

  private fun Connection.setGcFloor(userId: UserId, floor: Revision) {
    prepareStatement(
        "INSERT INTO sync_gc_floor (user_id, floor_revision) VALUES (?, ?) " +
          "ON CONFLICT (user_id) DO UPDATE SET floor_revision = EXCLUDED.floor_revision"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.setLong(2, floor.value)
        statement.executeUpdate()
      }
  }

  /** Inserts the row, or refreshes the platform and last seen time of the one already there. */
  private fun Connection.upsertDevice(
    userId: UserId,
    deviceId: DeviceId,
    platform: DevicePlatform,
    serverNow: Instant,
  ) {
    prepareStatement(
        "INSERT INTO sync_device (user_id, device_id, platform, last_seen_at) " +
          "VALUES (?, ?, ?, ?) ON CONFLICT (user_id, device_id) DO UPDATE SET " +
          "platform = EXCLUDED.platform, last_seen_at = EXCLUDED.last_seen_at"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.setString(2, deviceId.value)
        // The column is text, so the enum becomes its wire name here and nowhere else.
        statement.setString(3, platform.wireName)
        statement.setTimestamp(4, serverNow.toTimestamp())
        statement.executeUpdate()
      }
  }

  /**
   * Whether [deviceId] has confirmed committing every row [userId] has, or `null` when it has no
   * live row. Uses `>=` because collecting the newest tombstone can drop the maximum below
   * acknowledgements.
   */
  internal suspend fun deviceStatus(userId: UserId, deviceId: DeviceId): Boolean? =
    inTransaction { connection ->
      val device =
        connection.readDevice(userId, deviceId)?.takeIf { it.removedAt == null }
          ?: return@inTransaction null
      device.lastAcked >= connection.highestRevision(userId)
    }

  /**
   * The highest revision [userId] owns, or [Revision.ZERO]. Never the global `sync_revision`
   * sequence, which would report every device as behind forever.
   */
  private fun Connection.highestRevision(userId: UserId): Revision {
    val union =
      PER_USER_TABLES.joinToString(" UNION ALL ") {
        "SELECT max(revision) AS revision FROM $it WHERE user_id = ?"
      }
    return prepareStatement("SELECT COALESCE(max(revision), 0) FROM ($union) AS revisions").use {
      statement ->
      PER_USER_TABLES.forEachIndexed { index, _ -> statement.setString(index + 1, userId.value) }
      statement.executeQuery().use { rows ->
        rows.next()
        Revision(rows.getLong(1))
      }
    }
  }

  /** Forces a device's position, so floor boundaries are reachable without paging. Test only. */
  internal suspend fun setPositionForTest(
    userId: UserId,
    deviceId: DeviceId,
    lastAcked: Revision,
    lastServed: Revision,
  ) {
    inTransaction { connection ->
      connection
        .prepareStatement(
          "UPDATE sync_device SET last_acked_revision = ?, last_served_revision = ? " +
            "WHERE user_id = ? AND device_id = ?"
        )
        .use { statement ->
          statement.setLong(1, lastAcked.value)
          statement.setLong(2, lastServed.value)
          statement.setString(3, userId.value)
          statement.setString(4, deviceId.value)
          statement.executeUpdate()
        }
    }
  }

  /** Every device row [userId] owns, removed ones included. Test only. */
  internal suspend fun listDevicesForTest(userId: UserId): List<DeviceRow> =
    inTransaction { connection ->
      connection.readDevices(userId)
    }

  private fun Connection.readDevices(userId: UserId): List<DeviceRow> =
    prepareStatement(
        "SELECT device_id, platform, last_acked_revision, last_served_revision, " +
          "last_page_token, removed_at FROM sync_device WHERE user_id = ? ORDER BY device_id"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                DeviceRow(
                  deviceId = DeviceId(rows.getString(1)),
                  platform =
                    rows.getString(2).let(DevicePlatform::fromWire)
                      ?: error("stored platform '${rows.getString(2)}' is not a known one"),
                  lastAcked = Revision(rows.getLong(3)),
                  lastServed = Revision(rows.getLong(4)),
                  lastPageToken = rows.getString(5),
                  removedAt = rows.getTimestamp(6)?.toInstant()?.toKotlinInstant(),
                )
              )
            }
          }
        }
      }

  /**
   * Deletes every tombstone all registered devices have confirmed, one transaction per user so no
   * run holds every user's lock at once. Idempotent, so concurrent instances are safe.
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
        logger.error("Tombstone collection failed for user '{}', continuing", userId.value, e)
      }
    }
  }

  /** Collects one user's tombstones, in a single transaction under their lock. */
  private suspend fun collectTombstonesOf(userId: UserId) {
    inTransaction { connection ->
      connection.acquireUserLock(userId)
      // Read under the lock guarding the delete, or a concurrent registration could reinstate a
      // removed device below the floor written here.
      val watermark = connection.watermarkOf(userId)
      // Either some device has committed nothing yet, or there is genuinely nothing to reclaim.
      if (watermark == null || watermark == Revision.ZERO) return@inTransaction
      for (table in PER_USER_TABLES) {
        connection
          .prepareStatement("DELETE FROM $table WHERE user_id = ? AND is_deleted AND revision <= ?")
          .use { statement ->
            statement.setString(1, userId.value)
            statement.setLong(2, watermark.value)
            statement.executeUpdate()
          }
      }
      connection.setGcFloor(userId, watermark)
    }
  }

  /** Every user with at least one device row, which is the per user gate on collection. */
  private suspend fun usersWithDevices(): List<UserId> = inTransaction { connection ->
    connection.prepareStatement("SELECT DISTINCT user_id FROM sync_device").use { statement ->
      statement.executeQuery().use { rows ->
        buildList { while (rows.next()) add(UserId(rows.getString(1))) }
      }
    }
  }

  /**
   * The lowest acknowledgement across [userId]'s registered devices, or `null` when all are
   * removed. Removed devices are excluded so one lost install cannot hold the watermark down
   * forever.
   */
  private fun Connection.watermarkOf(userId: UserId): Revision? =
    prepareStatement(
        "SELECT min(last_acked_revision) FROM sync_device " +
          "WHERE user_id = ? AND removed_at IS NULL"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.executeQuery().use { rows ->
          rows.next()
          val value = rows.getLong(1)
          if (rows.wasNull()) null else Revision(value)
        }
      }

  /** The user's collection floor, or `null` when they have never been collected. Test only. */
  internal suspend fun gcFloorForTest(userId: UserId): Revision? = inTransaction { connection ->
    connection.prepareStatement("SELECT floor_revision FROM sync_gc_floor WHERE user_id = ?").use {
      statement ->
      statement.setString(1, userId.value)
      statement.executeQuery().use { rows -> if (rows.next()) Revision(rows.getLong(1)) else null }
    }
  }

  /**
   * Removes every per user row of [userId], leaving the shared `position` and `move_edge` rows.
   * Only half of account deletion: the identity must also be removed at the auth provider.
   */
  internal suspend fun deleteUser(userId: UserId) {
    inTransaction { connection ->
      for (table in PER_USER_TABLES + "sync_device" + "sync_gc_floor" + "sync_erase_cutoff") {
        connection.prepareStatement("DELETE FROM $table WHERE user_id = ?").use { statement ->
          statement.setString(1, userId.value)
          statement.executeUpdate()
        }
      }
    }
  }

  /**
   * Tombstones every live node, edge, repertoire and tag of [userId], records the erase cutoff and
   * returns the highest revision assigned, or [Revision.ZERO]. Each tombstone is stamped at least a
   * millisecond after the row it replaces, so it wins on every device.
   *
   * @throws UnknownDeviceException [deviceId] has no row under [userId].
   * @throws ResyncRequiredException [deviceId] was removed and has fallen below the garbage
   *   collection floor.
   */
  internal suspend fun erase(
    userId: UserId,
    deviceId: DeviceId,
    deviceSeq: Long,
    serverNow: Instant,
  ): Revision = inTransaction { connection ->
    connection.acquireUserLock(userId)
    connection.requireSyncableDevice(userId, deviceId)
    val revisions = ERASABLE_TABLES.flatMap {
      connection.tombstoneAll(it, userId, deviceId, deviceSeq, serverNow)
    }
    // With nothing live there is no tombstone to pull; a user owning nothing at all gets a fresh
    // revision, so no device counts as having pulled an erase it has not.
    val erasedRevision =
      revisions.maxOrNull()
        ?: connection.highestRevision(userId).takeIf { it > Revision.ZERO }
        ?: connection.nextRevision()
    connection.setEraseCutoff(
      userId,
      EraseCutoff(serverNow, erasedRevision, erasedBy = deviceId, eraseDeviceSeq = deviceSeq),
    )
    revisions.maxOrNull() ?: Revision.ZERO
  }

  private fun Connection.nextRevision(): Revision =
    prepareStatement("SELECT nextval('sync_revision')").use { statement ->
      statement.executeQuery().use { rows ->
        rows.next()
        Revision(rows.getLong(1))
      }
    }

  /** Tombstones every live row [userId] owns in [table], returning the revisions assigned. */
  private fun Connection.tombstoneAll(
    table: String,
    userId: UserId,
    deviceId: DeviceId,
    deviceSeq: Long,
    serverNow: Instant,
  ): List<Revision> =
    prepareStatement(
        "UPDATE $table SET is_deleted = true, " +
          "updated_at = GREATEST(?, updated_at + interval '1 millisecond'), " +
          "deleted_at = GREATEST(?, updated_at + interval '1 millisecond'), " +
          "origin_device = ?, device_seq = ?, revision = nextval('sync_revision') " +
          "WHERE user_id = ? AND NOT is_deleted RETURNING revision"
      )
      .use { statement ->
        statement.setTimestamp(1, serverNow.toTimestamp())
        statement.setTimestamp(2, serverNow.toTimestamp())
        statement.setString(3, deviceId.value)
        statement.setLong(4, deviceSeq)
        statement.setString(5, userId.value)
        statement.executeQuery().use { rows ->
          buildList { while (rows.next()) add(Revision(rows.getLong(1))) }
        }
      }

  private fun Connection.setEraseCutoff(userId: UserId, cutoff: EraseCutoff) {
    prepareStatement(
        "INSERT INTO sync_erase_cutoff " +
          "(user_id, erased_at, erased_revision, erased_by, erase_device_seq) " +
          "VALUES (?, ?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET " +
          "erased_at = EXCLUDED.erased_at, erased_revision = EXCLUDED.erased_revision, " +
          "erased_by = EXCLUDED.erased_by, erase_device_seq = EXCLUDED.erase_device_seq"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.setTimestamp(2, cutoff.erasedAt.toTimestamp())
        statement.setLong(3, cutoff.erasedRevision.value)
        statement.setString(4, cutoff.erasedBy.value)
        statement.setLong(5, cutoff.eraseDeviceSeq)
        statement.executeUpdate()
      }
  }

  /** [userId]'s last erase, or `null` when they never erased. */
  private fun Connection.eraseCutoffOf(userId: UserId): EraseCutoff? =
    prepareStatement(
        "SELECT erased_at, erased_revision, erased_by, erase_device_seq " +
          "FROM sync_erase_cutoff WHERE user_id = ?"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.executeQuery().use { rows ->
          if (!rows.next()) return@use null
          EraseCutoff(
            erasedAt = rows.getTimestamp(1).toInstant().toKotlinInstant(),
            erasedRevision = Revision(rows.getLong(2)),
            erasedBy = DeviceId(rows.getString(3)),
            eraseDeviceSeq = rows.getLong(4),
          )
        }
      }

  private fun Connection.pullRepertoires(
    userId: UserId,
    since: Revision,
    limit: Int,
  ): List<Pair<Revision, RepertoireSyncRow>> =
    prepareStatement(
        "SELECT repertoire_id, name, color, is_deleted, updated_at, origin_device, device_seq, " +
          "revision, installed_version FROM user_repertoire WHERE user_id = ? AND revision > ? " +
          "ORDER BY revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.setLong(2, since.value)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                Revision(rows.getLong(8)) to
                  RepertoireSyncRow(
                    id = rows.getString(1),
                    name = rows.getString(2),
                    color = rows.getString(3)?.let(RepertoireColor::fromNameOrNull),
                    installedVersion = rows.getObject(9) as Int?,
                    isDeleted = rows.getBoolean(4),
                    updatedAt = rows.getTimestamp(5).toInstant().toKotlinInstant(),
                    originDevice = DeviceId(rows.getString(6)),
                    deviceSeq = rows.getLong(7),
                  )
              )
            }
          }
        }
      }

  private fun Connection.pullTags(
    userId: UserId,
    since: Revision,
    limit: Int,
  ): List<Pair<Revision, EdgeRepertoireTagSyncRow>> =
    prepareStatement(
        "SELECT po.position_key, pd.position_key, t.repertoire_id, t.is_deleted, t.updated_at, " +
          "t.origin_device, t.device_seq, t.revision, e.move, t.is_good " +
          "FROM user_edge_repertoire_tag t " +
          "JOIN move_edge e ON e.id = t.edge_id " +
          JOIN_EDGE_ENDPOINTS +
          "WHERE t.user_id = ? AND t.revision > ? ORDER BY t.revision ASC LIMIT ?"
      )
      .use { statement ->
        statement.setString(1, userId.value)
        statement.setLong(2, since.value)
        statement.setInt(3, limit)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) {
              add(
                Revision(rows.getLong(8)) to
                  EdgeRepertoireTagSyncRow(
                    origin = rows.getString(1),
                    destination = rows.getString(2),
                    repertoireId = rows.getString(3),
                    move = rows.getString(9),
                    isGood = rows.getObject(10) as Boolean?,
                    isDeleted = rows.getBoolean(4),
                    updatedAt = rows.getTimestamp(5).toInstant().toKotlinInstant(),
                    originDevice = DeviceId(rows.getString(6)),
                    deviceSeq = rows.getLong(7),
                  )
              )
            }
          }
        }
      }

  /**
   * Writes [rows] under last write wins and returns the revisions assigned, skipping identical
   * replays. A losing row still advances the survivor's revision, so the pusher is served it again.
   */
  private fun <R : SyncRow, K> Connection.applyAll(
    userId: UserId,
    table: SyncTable<R, K>,
    rows: List<R>,
    keyOf: (R) -> K,
  ): List<Revision> {
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
    val lastPerKey = LinkedHashMap<K, Pair<R, Revision>>()
    decided.forEachIndexed { index, (key, row) -> lastPerKey[key] = row to revisions[index] }
    upsertAll(userId, table, lastPerKey)
    return revisions
  }

  /** The stored rows of the keys in [keyed], by key, locked for the rest of the transaction. */
  private fun <R : SyncRow, K> Connection.readAllLocked(
    userId: UserId,
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
      statement.setString(table.keyTypes.size + 1, userId.value)
      statement.executeQuery().use { rows ->
        val versionStart = table.payloadColumns.size + 1
        val keyStart = versionStart + STORED_VERSION_COLUMNS.size
        while (rows.next()) {
          val key = table.readKey(rows, keyStart)
          val version =
            StoredVersion(
              isDeleted = rows.getBoolean(versionStart),
              updatedAt = rows.getTimestamp(versionStart + 1).toInstant().toKotlinInstant(),
              originDevice = DeviceId(rows.getString(versionStart + 2)),
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
    userId: UserId,
    table: SyncTable<R, K>,
    writes: Map<K, Pair<R, Revision>>,
  ) {
    prepareStatement(table.upsertSql).use { statement ->
      for ((key, versioned) in writes) {
        val (row, revision) = versioned
        statement.setString(1, userId.value)
        table.keyParts(key).forEachIndexed { index, part -> statement.setObject(2 + index, part) }
        val first = 2 + table.keyColumns.size
        table.bindPayload(statement, first, row)
        val tail = first + table.payloadColumns.size
        statement.setBoolean(tail, row.isDeleted)
        statement.setTimestamp(tail + 1, if (row.isDeleted) row.updatedAt.toTimestamp() else null)
        statement.setTimestamp(tail + 2, row.updatedAt.toTimestamp())
        statement.setString(tail + 3, row.originDevice.value)
        statement.setLong(tail + 4, row.deviceSeq)
        statement.setLong(tail + 5, revision.value)
        statement.addBatch()
      }
      statement.executeBatch()
    }
  }

  /** [count] fresh revisions, ascending. */
  private fun Connection.nextRevisions(count: Int): List<Revision> =
    prepareStatement("SELECT nextval('sync_revision') FROM generate_series(1, ?)").use { statement
      ->
      statement.setInt(1, count)
      statement.executeQuery().use { rows ->
        buildList { while (rows.next()) add(Revision(rows.getLong(1))) }.sorted()
      }
    }

  /**
   * Serializes [userId]'s pushes for the rest of the transaction, so two concurrent quota checks
   * cannot both pass against the same stale count.
   */
  private fun Connection.acquireUserLock(userId: UserId) {
    prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").use { statement ->
      statement.setString(1, "sync-user:${userId.value}")
      statement.executeQuery().use { it.next() }
    }
  }

  /** Rows [userId] already owns in [table]. [table] is always one of this file's own literals. */
  private fun Connection.countUserRows(table: String, userId: UserId): Int =
    prepareStatement("SELECT count(*) FROM $table WHERE user_id = ?").use { statement ->
      statement.setString(1, userId.value)
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getInt(1)
      }
    }

  /** @throws QuotaExceededException [incoming] would push [userId] past [cap] rows of [rule]. */
  private fun <R, I> Connection.checkQuota(
    userId: UserId,
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

/** Refused because the batch would push some resource past its per user cap; nothing is stored. */
internal class QuotaExceededException(message: String) : Exception(message)

/** Locks the selected row, so two concurrent pushes for one key cannot both decide they win. */
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
  val originDevice: DeviceId,
  val deviceSeq: Long,
)

/**
 * How one per user table stores rows of type [R], keyed by [K], under last write wins.
 *
 * @property keyColumns Columns that, beside `user_id`, identify one row, in [keyParts] order.
 * @property payloadColumns The row's own data, in [bindPayload] and [readPayload] order.
 * @property readKey Rebuilds a key from [keyColumns], starting at the given result set index.
 * @property bindPayload Binds a row's data, starting at the given parameter index.
 * @property readPayload Rebuilds the stored row from index `1`, taking its identity from the
 *   incoming row it is resolved against.
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

  /** Selects and locks one user's rows whose key is in the bound arrays: payload, version, key. */
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
    payloadColumns = listOf("name", "color", "installed_version"),
    keyTypes = listOf("text"),
    keyParts = { listOf(it) },
    readKey = { rows, index -> rows.getString(index) },
    bindPayload = { statement, first, row ->
      statement.setString(first, row.name)
      statement.setString(first + 1, row.color?.name)
      statement.setObject(first + 2, row.installedVersion, Types.INTEGER)
    },
    readPayload = { rows, incoming, version ->
      RepertoireSyncRow(
        id = incoming.id,
        name = rows.getString(1),
        color = rows.getString(2)?.let(RepertoireColor::fromNameOrNull),
        installedVersion = rows.getObject(3) as Int?,
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
    payloadColumns = listOf("is_good"),
    keyTypes = listOf("bigint", "text"),
    keyParts = { (edgeId, repertoireId) -> listOf(edgeId, repertoireId) },
    readKey = { rows, index -> rows.getLong(index) to rows.getString(index + 1) },
    bindPayload = { statement, first, row ->
      statement.setObject(first, row.isGood, Types.BOOLEAN)
    },
    readPayload = { rows, incoming, version ->
      EdgeRepertoireTagSyncRow(
        origin = incoming.origin,
        destination = incoming.destination,
        repertoireId = incoming.repertoireId,
        move = incoming.move,
        isGood = rows.getObject(1) as Boolean?,
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
  val countStored: (Connection, UserId, List<I>) -> Int,
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
private fun countStoredAmong(sql: String): (Connection, UserId, List<String>) -> Int =
  { connection, userId, keys ->
    connection.prepareStatement(sql).use { statement ->
      statement.setString(1, userId.value)
      statement.setArray(2, connection.createArrayOf("text", keys.toTypedArray()))
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getInt(1)
      }
    }
  }

/**
 * Counts in one query, [sql] taking one text array per entry of [partsOf], then the user id. Counts
 * rows rather than identities, so the identities must be distinct.
 */
private fun <I> countStoredAmongTuples(
  sql: String,
  partsOf: (I) -> List<String>,
): (Connection, UserId, List<I>) -> Int = { connection, userId, identities ->
  val parts = identities.map(partsOf)
  connection.prepareStatement(sql).use { statement ->
    val width = parts.first().size
    for (column in 0 until width) {
      statement.setArray(
        column + 1,
        connection.createArrayOf("text", parts.map { it[column] }.toTypedArray()),
      )
    }
    statement.setString(width + 1, userId.value)
    statement.executeQuery().use { rows ->
      rows.next()
      rows.getInt(1)
    }
  }
}

/** The tables holding per user rows. The shared `position` and `move_edge` are not among them. */
private val PER_USER_TABLES =
  listOf("user_node", "user_edge", "user_setting", "user_repertoire", "user_edge_repertoire_tag")

/** The per user tables an erase tombstones: everything but settings, which survive it. */
private val ERASABLE_TABLES =
  listOf("user_node", "user_edge", "user_repertoire", "user_edge_repertoire_tag")

/** Screening outcome for one resource: what may be written, and what was refused. */
private class Screened<T : SyncRow>(val accepted: List<T>, val refused: List<RejectedRow>)

/** Splits rows into those the server may store and those whose clock is too far ahead. */
private fun <T : SyncRow> List<T>.screenClock(
  serverNow: Instant,
  refusal: (T) -> RejectedRow,
): Screened<T> {
  val (tooFarAhead, acceptable) = partition { it.isTooFarAhead(serverNow) }
  return Screened(acceptable, tooFarAhead.map(refusal))
}

/**
 * A user's last erase: when it happened, the highest revision it assigned, and the device and
 * `deviceSeq` that issued it.
 */
private data class EraseCutoff(
  val erasedAt: Instant,
  val erasedRevision: Revision,
  val erasedBy: DeviceId,
  val eraseDeviceSeq: Long,
) {
  /**
   * Whether [row], pushed by a device that has not pulled this erase, predates it. The eraser's own
   * writes after the erase never do, whatever its clock says.
   */
  fun cuts(row: SyncRow): Boolean =
    row.updatedAt < erasedAt && !(row.originDevice == erasedBy && row.deviceSeq > eraseDeviceSeq)
}

/** Returns this node as an erase tombstone when [cutoff] cuts it, else as is. */
private fun NodeSyncRow.cutOffAt(cutoff: EraseCutoff) =
  if (!cutoff.cuts(this)) this
  else
    copy(
      isDeleted = true,
      updatedAt = cutoff.erasedAt,
      originDevice = DeviceId.ERASE_ORIGIN,
      deviceSeq = 0,
    )

/** Returns this edge as an erase tombstone when [cutoff] cuts it, else as is. */
private fun EdgeSyncRow.cutOffAt(cutoff: EraseCutoff) =
  if (!cutoff.cuts(this)) this
  else
    copy(
      isDeleted = true,
      updatedAt = cutoff.erasedAt,
      originDevice = DeviceId.ERASE_ORIGIN,
      deviceSeq = 0,
    )

/** Returns this repertoire as an erase tombstone when [cutoff] cuts it, else as is. */
private fun RepertoireSyncRow.cutOffAt(cutoff: EraseCutoff) =
  if (!cutoff.cuts(this)) this
  else
    copy(
      isDeleted = true,
      updatedAt = cutoff.erasedAt,
      originDevice = DeviceId.ERASE_ORIGIN,
      deviceSeq = 0,
    )

/** Returns this tag as an erase tombstone when [cutoff] cuts it, else as is. */
private fun EdgeRepertoireTagSyncRow.cutOffAt(cutoff: EraseCutoff) =
  if (!cutoff.cuts(this)) this
  else
    copy(
      isDeleted = true,
      updatedAt = cutoff.erasedAt,
      originDevice = DeviceId.ERASE_ORIGIN,
      deviceSeq = 0,
    )

/** Stable identifier for an edge in a rejection report, matching what the client can compute. */
private fun EdgeSyncRow.edgeId(): String = "$origin|$destination"

/** The shared identity this edge interns to. */
private fun EdgeSyncRow.identity() = EdgeIdentity(origin, destination, move)

private fun EdgeRepertoireTagSyncRow.identity() = EdgeIdentity(origin, destination, move)

private fun clockRefusal(kind: String, id: String) =
  RejectedRow(
    kind = kind,
    id = id,
    code = RejectionCode.CLOCK_TOO_FAR_AHEAD,
    reason = "updatedAt is further ahead of server time than the tolerance allows",
  )

/**
 * Converts without losing precision: a millisecond round trip would truncate the microseconds
 * `timestamptz` keeps, so a stored row would no longer match the row that was sent.
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
  val deviceId: DeviceId,
  val platform: DevicePlatform,
  val lastAcked: Revision,
  val lastServed: Revision,
  val lastPageToken: String?,
  val removedAt: Instant?,
)

private val logger = LoggerFactory.getLogger(SyncStore::class.java)

/** A caller named a device the server has never been told about. */
internal class UnknownDeviceException(deviceId: DeviceId) :
  Exception("device '${deviceId.value}' is not registered")

/** A removed device came back below what garbage collection already purged. */
internal class ResyncRequiredException(deviceId: DeviceId) :
  Exception("device '${deviceId.value}' must resync from scratch")
