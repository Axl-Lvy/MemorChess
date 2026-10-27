package proj.memorchess.axl.server.repertoire

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.data.repertoire.RepertoirePublishLimits
import proj.memorchess.axl.server.db.EdgeIdentity
import proj.memorchess.axl.server.db.resolveEdgeIds

/** Largest number of non removed repertoires one author may own at once. */
internal const val MAX_REPERTOIRES_PER_USER: Int = 20

/** Largest total move count, summed across an author's non removed repertoires. */
internal const val MAX_TOTAL_MOVES_PER_USER: Int = 50_000

/** Largest number of rows [RepertoireStore.allPublished] will ever return. */
private const val MAX_MANIFEST_ROWS: Int = 10_000

/** One stored version of a repertoire: exactly one row of `repertoire_version`. */
internal data class RepertoireRow(
  val id: String,
  val version: Int,
  val authorId: String,
  val title: String,
  val description: String,
  val side: String,
  val moveCount: Int,
  val status: String,
  val publishedAt: Instant,
)

/**
 * The caller-supplied fields of a publish call, grouped so [RepertoireStore] can pass them as one.
 */
private data class PublishInput(
  val id: String,
  val title: String,
  val description: String,
  val side: String,
  val moves: List<RepertoireMove>,
)

/** Outcome of [RepertoireStore.publish]. */
internal sealed class PublishOutcome {
  /** The version was stored. */
  data class Published(val row: RepertoireRow) : PublishOutcome()

  /** The moves are empty, illegal or disconnected, or the id, side, title or description is bad. */
  data class InvalidPayload(val reason: String) : PublishOutcome()

  /** The move count or ply depth exceeds a server side cap. */
  data class PayloadTooLarge(val reason: String) : PublishOutcome()

  /** The id already belongs to a different author. */
  data object Forbidden : PublishOutcome()

  /** The id's latest version was removed by a moderator. Republishing over that is refused. */
  data object Removed : PublishOutcome()

  /** Publishing would exceed a per author quota. */
  data class QuotaExceeded(val reason: String) : PublishOutcome()
}

/** Outcome of [RepertoireStore.remove]. */
internal sealed class RemoveOutcome {
  data object Removed : RemoveOutcome()

  data object NotFound : RemoveOutcome()

  data object Forbidden : RemoveOutcome()
}

/** Outcome of [RepertoireStore.setStatus]. */
internal sealed class SetStatusOutcome {
  data class Updated(val row: RepertoireRow) : SetStatusOutcome()

  data object NotFound : SetStatusOutcome()
}

/**
 * Result of a page read: the rows and the cursor to pass for the next page, or `null` when done.
 */
internal data class RepertoirePage(val rows: List<RepertoireRow>, val nextCursor: String?)

/**
 * The server side of published repertoires, over a Postgres database.
 *
 * A version is its `repertoire_version` row plus one `repertoire_edge` row per move, written in one
 * transaction. Every lookup reads the **latest version** of an id and its status. An older
 * version's status is never consulted once a newer one exists.
 *
 * @param dataSource Pooled connections. Each call takes one and returns it.
 * @param maxMoves Cap on one publish's move count.
 * @param maxPlyDepth Cap on how deep from the starting position a published move may start.
 * @param maxRepertoiresPerUser Cap on how many non removed repertoires one author may own.
 * @param maxTotalMovesPerUser Cap on the summed move count of one author's non removed repertoires.
 * @param ioDispatcher Where the blocking JDBC work runs. Injected so a caller can substitute one.
 */
internal class RepertoireStore(
  private val dataSource: DataSource,
  private val maxMoves: Int = RepertoirePublishLimits.MAX_REPERTOIRE_MOVES,
  private val maxPlyDepth: Int = RepertoirePublishLimits.MAX_PLY_DEPTH,
  private val maxRepertoiresPerUser: Int = MAX_REPERTOIRES_PER_USER,
  private val maxTotalMovesPerUser: Int = MAX_TOTAL_MOVES_PER_USER,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  /**
   * Validates and stores a new version of [id], authored by [authorId].
   *
   * The moves are validated with [RepertoireMovesValidator] before anything is written. A first
   * publish of [id] creates version 1. A later publish by the same author creates the next version
   * and never mutates an earlier one. A publish by a different author than the one who already owns
   * [id] is refused, never silently reassigned.
   */
  suspend fun publish(
    authorId: String,
    id: String,
    title: String,
    description: String,
    side: String,
    moves: List<RepertoireMove>,
    now: Instant,
  ): PublishOutcome {
    RepertoirePublishLimits.idProblem(id)?.let {
      return PublishOutcome.InvalidPayload(it)
    }
    if (side != "white" && side != "black") {
      return PublishOutcome.InvalidPayload("side must be 'white' or 'black', was '$side'")
    }
    if (title.length > RepertoirePublishLimits.MAX_TITLE_LENGTH) {
      return PublishOutcome.InvalidPayload(
        "title must be at most ${RepertoirePublishLimits.MAX_TITLE_LENGTH} characters," +
          " was ${title.length}"
      )
    }
    if (description.length > RepertoirePublishLimits.MAX_DESCRIPTION_LENGTH) {
      return PublishOutcome.InvalidPayload(
        "description must be at most ${RepertoirePublishLimits.MAX_DESCRIPTION_LENGTH}" +
          " characters, was ${description.length}"
      )
    }

    val validation =
      withContext(ioDispatcher) { RepertoireMovesValidator.validate(moves, maxMoves, maxPlyDepth) }
    return when (validation) {
      is MoveListValidation.Rejected -> PublishOutcome.InvalidPayload(validation.reason)
      is MoveListValidation.TooLarge -> PublishOutcome.PayloadTooLarge(validation.reason)
      is MoveListValidation.Valid ->
        doPublish(authorId, PublishInput(id, title, description, side, moves), now)
    }
  }

  /**
   * Inserts the version row and its moves in one transaction.
   *
   * The transaction holds [acquireRepertoireLock] on [input]'s id for its whole duration, so two
   * concurrent publishes (or a double click) for the same id never compute the same next version
   * number.
   */
  private suspend fun doPublish(
    authorId: String,
    input: PublishInput,
    now: Instant,
  ): PublishOutcome {
    val (id, title, description, side, moves) = input
    return inTransaction { connection ->
      connection.acquireRepertoireLock(id)
      val existing = connection.lockLatestVersion(id)
      if (existing != null && existing.authorId != authorId) {
        return@inTransaction PublishOutcome.Forbidden
      }

      // A removed id counts against the quota the same as a brand new one: without this, an
      // author at the cap can free a slot by removing a repertoire and then republish the same id
      // to occupy it again, over and over, never actually reducing their footprint.
      val republishingRemoved = existing != null && existing.status == STATUS_REMOVED
      val (otherCount, otherMoves) = connection.authorFootprint(authorId, excludingId = id)
      if ((existing == null || republishingRemoved) && otherCount >= maxRepertoiresPerUser) {
        return@inTransaction PublishOutcome.QuotaExceeded(
          "you already own $otherCount repertoires, the cap is $maxRepertoiresPerUser"
        )
      }
      val projectedMoves = otherMoves + moves.size
      if (projectedMoves > maxTotalMovesPerUser) {
        return@inTransaction PublishOutcome.QuotaExceeded(
          "publishing would use $projectedMoves of your $maxTotalMovesPerUser move quota"
        )
      }
      // A moderator's removal is a latch, not a mutable flag an author can clear by republishing.
      // setStatus refuses the same reversal from the moderation side; this is its publish side
      // counterpart.
      if (republishingRemoved) {
        return@inTransaction PublishOutcome.Removed
      }

      val row =
        RepertoireRow(
          id = id,
          version = (existing?.version ?: 0) + 1,
          authorId = authorId,
          title = title,
          description = description,
          side = side,
          moveCount = moves.size,
          status = "published",
          publishedAt = now,
        )
      connection.insertVersion(row)
      connection.insertMoves(row, moves)
      PublishOutcome.Published(row)
    }
  }

  /**
   * Marks every version of [id] as removed, so none of them is listed or downloadable any more.
   *
   * Every version, not only the latest: an old version's row would otherwise keep its original
   * `published` status forever, and its moves would stay downloadable after the id is taken down.
   */
  suspend fun remove(authorId: String, id: String): RemoveOutcome = inTransaction { connection ->
    connection.acquireRepertoireLock(id)
    val existing = connection.lockLatestVersion(id)
    when {
      existing == null || existing.status == STATUS_REMOVED -> RemoveOutcome.NotFound
      existing.authorId != authorId -> RemoveOutcome.Forbidden
      else -> {
        connection.updateStatusForEveryVersion(id, STATUS_REMOVED)
        RemoveOutcome.Removed
      }
    }
  }

  /**
   * Moderation kill switch: sets [id]'s status to [status] regardless of author. When [status] is
   * `removed`, this cascades to every version of [id], for the reason explained on [remove]'s KDoc.
   * Any other status only ever touches the latest version, matching [get] and [listPublished],
   * which never consult an older version's status once a newer one exists.
   */
  suspend fun setStatus(id: String, status: String): SetStatusOutcome =
    inTransaction { connection ->
      connection.acquireRepertoireLock(id)
      val existing = connection.lockLatestVersion(id)
      // Removal is a latch: moving a removed repertoire back to published or unlisted is treated
      // the same as an unknown id rather than as a resurrection the moderator has to know to avoid.
      if (existing == null || existing.status == STATUS_REMOVED) {
        return@inTransaction SetStatusOutcome.NotFound
      }
      if (status == STATUS_REMOVED) {
        connection.updateStatusForEveryVersion(id, STATUS_REMOVED)
      } else {
        connection.updateStatus(id, existing.version, status)
      }
      SetStatusOutcome.Updated(existing.copy(status = status))
    }

  /** The latest version of [id], or `null` when unknown or its latest version was removed. */
  suspend fun get(id: String): RepertoireRow? =
    withContext(ioDispatcher) {
      dataSource.connection.use { connection ->
        connection.latestVersion(id)?.takeUnless { it.status == STATUS_REMOVED }
      }
    }

  /**
   * One page of published repertoires (one row per id, its latest version), ordered by id.
   *
   * @param cursor The last id of the previous page, or `null` for the first page.
   * @param limit Maximum rows to return. Must be strictly positive.
   */
  suspend fun listPublished(cursor: String?, limit: Int): RepertoirePage {
    require(limit > 0) { "limit must be strictly positive, was $limit" }
    return withContext(ioDispatcher) {
      dataSource.connection.use { connection ->
        val rows = connection.publishedLatestVersions(cursor, limit)
        RepertoirePage(rows, rows.takeIf { it.size == limit }?.last()?.id)
      }
    }
  }

  /** Every published repertoire, unpaginated, for `manifest.json`. */
  suspend fun allPublished(): List<RepertoireRow> =
    withContext(ioDispatcher) {
      dataSource.connection.use { connection ->
        connection.publishedLatestVersions(cursor = null, limit = MAX_MANIFEST_ROWS)
      }
    }

  /**
   * The moves of [id]'s [version] with their markings, or `null` when that version does not exist
   * or was removed.
   */
  suspend fun moves(id: String, version: Int): List<RepertoireMove>? =
    withContext(ioDispatcher) {
      dataSource.connection.use { connection ->
        val status = connection.statusOf(id, version)
        if (status == null || status == STATUS_REMOVED) null else connection.movesOf(id, version)
      }
    }

  /**
   * Records one more anonymous install of [id], for the popularity hint the catalog's
   * `downloadCount` field carries. Keyed by [id] alone (not `(id, version)`), so a later republish
   * under a new version does not reset the count. [id] need not be a currently published
   * repertoire; recording one anyway is harmless (see the `repertoire_install_count` table's own
   * comment in `schema.sql`).
   */
  suspend fun recordInstall(id: String) {
    withContext(ioDispatcher) {
      dataSource.connection.use { connection -> connection.incrementInstallCount(id) }
    }
  }

  /**
   * The recorded install count of every id in [ids], defaulting to `0` for one with no
   * [recordInstall] call yet. The returned map has exactly one entry per (distinct) id in [ids],
   * regardless of order.
   */
  suspend fun countsFor(ids: List<String>): Map<String, Long> {
    if (ids.isEmpty()) return emptyMap()
    val distinctIds = ids.distinct()
    return withContext(ioDispatcher) {
      dataSource.connection.use { connection -> connection.installCountsFor(distinctIds) }
    }
  }

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
}

private const val STATUS_REMOVED = "removed"

/**
 * Serializes every publish, remove and status change for [id] against each other for the rest of
 * the transaction, released on commit or rollback.
 *
 * Postgres's default READ COMMITTED isolation does not by itself stop two concurrent transactions
 * from both reading the same "current latest version" and computing the same next version number,
 * which then collides on the `(id, version)` primary key. Taking this lock first, before either
 * transaction reads anything, forces the second one to wait and see the first one's write.
 */
private fun Connection.acquireRepertoireLock(id: String) {
  prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").use { statement ->
    statement.setString(1, id)
    statement.executeQuery().use { it.next() }
  }
}

/**
 * Locks and returns the latest version row for [id], or `null` when [id] has never been published.
 */
private fun Connection.lockLatestVersion(id: String): RepertoireRow? =
  prepareStatement(
      "SELECT $ROW_COLUMNS FROM repertoire_version WHERE id = ? ORDER BY version DESC LIMIT 1 FOR UPDATE"
    )
    .use { statement ->
      statement.setString(1, id)
      statement.executeQuery().use { rows -> if (rows.next()) rows.toRow() else null }
    }

/** The latest version row for [id], unlocked. */
private fun Connection.latestVersion(id: String): RepertoireRow? =
  prepareStatement(
      "SELECT $ROW_COLUMNS FROM repertoire_version WHERE id = ? ORDER BY version DESC LIMIT 1"
    )
    .use { statement ->
      statement.setString(1, id)
      statement.executeQuery().use { rows -> if (rows.next()) rows.toRow() else null }
    }

/**
 * The count and summed move count of [authorId]'s non removed repertoires, one per id (its latest
 * version), excluding [excludingId].
 */
private fun Connection.authorFootprint(authorId: String, excludingId: String): Pair<Int, Int> =
  prepareStatement(
      "SELECT count(*), COALESCE(sum(move_count), 0) FROM (" +
        "SELECT DISTINCT ON (id) id, move_count, status FROM repertoire_version " +
        "WHERE author_id = ? ORDER BY id, version DESC" +
        ") latest WHERE status != ? AND id != ?"
    )
    .use { statement ->
      statement.setString(1, authorId)
      statement.setString(2, STATUS_REMOVED)
      statement.setString(3, excludingId)
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getInt(1) to rows.getInt(2)
      }
    }

/**
 * Upserts [id]'s row in `repertoire_install_count`, starting at 1 or incrementing an existing row.
 * The `ON CONFLICT` clause resolves entirely inside Postgres, so two concurrent calls for the same
 * [id] never race the way a separate read-then-write would.
 */
private fun Connection.incrementInstallCount(id: String) {
  prepareStatement(
      "INSERT INTO repertoire_install_count (id, count) VALUES (?, 1) " +
        "ON CONFLICT (id) DO UPDATE SET count = repertoire_install_count.count + 1"
    )
    .use { statement ->
      statement.setString(1, id)
      statement.executeUpdate()
    }
}

/** The recorded install count of every id in [ids], defaulting to 0 for one with no row yet. */
private fun Connection.installCountsFor(ids: List<String>): Map<String, Long> {
  val counts = mutableMapOf<String, Long>()
  ids.associateWithTo(counts) { 0L }
  prepareStatement("SELECT id, count FROM repertoire_install_count WHERE id = ANY(?)").use {
    statement ->
    statement.setArray(1, createArrayOf("text", ids.toTypedArray()))
    statement.executeQuery().use { rows ->
      while (rows.next()) {
        counts[rows.getString(1)] = rows.getLong(2)
      }
    }
  }
  return counts
}

/** Sets [status] on every version row of [id], not only its latest. */
private fun Connection.updateStatusForEveryVersion(id: String, status: String) {
  prepareStatement("UPDATE repertoire_version SET status = ? WHERE id = ?").use { statement ->
    statement.setString(1, status)
    statement.setString(2, id)
    statement.executeUpdate()
  }
}

private fun Connection.insertVersion(row: RepertoireRow) {
  prepareStatement(
      "INSERT INTO repertoire_version (id, version, author_id, title, description, side, " +
        "move_count, status, published_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
    )
    .use { statement ->
      statement.setString(1, row.id)
      statement.setInt(2, row.version)
      statement.setString(3, row.authorId)
      statement.setString(4, row.title)
      statement.setString(5, row.description)
      statement.setString(6, row.side)
      statement.setInt(7, row.moveCount)
      statement.setString(8, row.status)
      statement.setTimestamp(9, row.publishedAt.toSqlTimestamp())
      statement.executeUpdate()
    }
}

/** Interns [moves] into `move_edge` and records each one, with its marking, as part of [row]. */
private fun Connection.insertMoves(row: RepertoireRow, moves: List<RepertoireMove>) {
  val edgeIds = resolveEdgeIds(moves.map { EdgeIdentity(it.from, it.to, it.move) })
  prepareStatement(
      "INSERT INTO repertoire_edge (repertoire_id, version, edge_id, is_good) VALUES (?, ?, ?, ?)"
    )
    .use { statement ->
      for (move in moves) {
        statement.setString(1, row.id)
        statement.setInt(2, row.version)
        statement.setLong(3, edgeIds.getValue(EdgeIdentity(move.from, move.to, move.move)))
        statement.setBoolean(4, move.isGood)
        statement.addBatch()
      }
      statement.executeBatch()
    }
}

/** The status of [id]'s [version], or `null` when that version does not exist. */
private fun Connection.statusOf(id: String, version: Int): String? =
  prepareStatement("SELECT status FROM repertoire_version WHERE id = ? AND version = ?").use {
    statement ->
    statement.setString(1, id)
    statement.setInt(2, version)
    statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
  }

/** Every move of [id]'s [version], in a stable order. */
private fun Connection.movesOf(id: String, version: Int): List<RepertoireMove> =
  prepareStatement(
      "SELECT o.position_key, e.move, d.position_key, r.is_good FROM repertoire_edge r " +
        "JOIN move_edge e ON e.id = r.edge_id " +
        "JOIN position o ON o.id = e.origin_id " +
        "JOIN position d ON d.id = e.destination_id " +
        "WHERE r.repertoire_id = ? AND r.version = ? ORDER BY e.id"
    )
    .use { statement ->
      statement.setString(1, id)
      statement.setInt(2, version)
      statement.executeQuery().use { rows ->
        buildList {
          while (rows.next()) {
            add(
              RepertoireMove(
                rows.getString(1),
                rows.getString(2),
                rows.getString(3),
                rows.getBoolean(4),
              )
            )
          }
        }
      }
    }

private fun Connection.updateStatus(id: String, version: Int, status: String) {
  prepareStatement("UPDATE repertoire_version SET status = ? WHERE id = ? AND version = ?").use {
    statement ->
    statement.setString(1, status)
    statement.setString(2, id)
    statement.setInt(3, version)
    statement.executeUpdate()
  }
}

/** One row per id (its latest version), published only, in id order, starting after [cursor]. */
private fun Connection.publishedLatestVersions(cursor: String?, limit: Int): List<RepertoireRow> {
  val sql = buildString {
    append("SELECT $ROW_COLUMNS FROM (")
    append("SELECT DISTINCT ON (id) $ROW_COLUMNS FROM repertoire_version ORDER BY id, version DESC")
    append(") latest WHERE status = ?")
    if (cursor != null) append(" AND id > ?")
    append(" ORDER BY id LIMIT ?")
  }
  return prepareStatement(sql).use { statement ->
    var index = 1
    statement.setString(index++, "published")
    if (cursor != null) statement.setString(index++, cursor)
    statement.setInt(index, limit)
    statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toRow()) } }
  }
}

private const val ROW_COLUMNS =
  "id, version, author_id, title, description, side, move_count, status, published_at"

private fun ResultSet.toRow(): RepertoireRow =
  RepertoireRow(
    id = getString(1),
    version = getInt(2),
    authorId = getString(3),
    title = getString(4),
    description = getString(5),
    side = getString(6),
    moveCount = getInt(7),
    status = getString(8),
    publishedAt = getTimestamp(9).toInstant().toKotlinInstant(),
  )

/**
 * Converts without losing precision, duplicated from `sync/SyncStore.kt` rather than shared with
 * it: `kotlin.time.Instant` carries nanoseconds and `timestamptz` carries microseconds, so a
 * millisecond round trip would silently move a timestamp.
 */
private fun Instant.toSqlTimestamp(): Timestamp =
  Timestamp.from(java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()))

private fun java.time.Instant.toKotlinInstant(): Instant =
  Instant.fromEpochSeconds(epochSecond, nano.toLong())
