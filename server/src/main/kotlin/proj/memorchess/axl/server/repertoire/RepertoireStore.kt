package proj.memorchess.axl.server.repertoire

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.data.repertoire.RepertoirePublishLimits
import proj.memorchess.axl.server.auth.UserId
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
  val authorId: UserId,
  val title: String,
  val description: String,
  val side: RepertoireColor,
  val moveCount: Int,
  val status: RepertoireStatus,
  val publishedAt: Instant,
)

/** The caller-supplied fields of a publish call. */
private data class PublishInput(
  val id: String,
  val title: String,
  val description: String,
  val side: RepertoireColor,
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

/** One page of rows and the cursor for the next page, or `null` when done. */
internal data class RepertoirePage(val rows: List<RepertoireRow>, val nextCursor: String?)

/**
 * Published repertoires stored in Postgres. Every lookup reads only the latest version of an id and
 * its status.
 *
 * @param maxPlyDepth Cap on how deep from the starting position a published move may start.
 * @param maxRepertoiresPerUser Cap on how many non removed repertoires one author may own.
 * @param maxTotalMovesPerUser Cap on the summed move count of one author's non removed repertoires.
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
   * Validates and stores a new version of [id] authored by [authorId]. Earlier versions are never
   * mutated, and an id owned by another author is refused.
   */
  suspend fun publish(
    authorId: UserId,
    id: String,
    title: String,
    description: String,
    side: RepertoireColor,
    moves: List<RepertoireMove>,
    now: Instant,
  ): PublishOutcome {
    RepertoirePublishLimits.idProblem(id)?.let {
      return PublishOutcome.InvalidPayload(it)
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
   * Inserts the version row and its moves in one transaction, holding [acquireRepertoireLock] so
   * concurrent publishes of one id never compute the same version number.
   */
  private suspend fun doPublish(
    authorId: UserId,
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

      // A removed id counts as new, or an author at the cap could cycle remove and republish.
      val republishingRemoved = existing != null && existing.status == RepertoireStatus.REMOVED
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
      // A removal is a latch an author cannot clear by republishing.
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
          status = RepertoireStatus.PUBLISHED,
          publishedAt = now,
        )
      connection.insertVersion(row)
      connection.insertMoves(row, moves)
      PublishOutcome.Published(row)
    }
  }

  /** Marks every version of [id] as removed, so none of them is listed or downloadable any more. */
  suspend fun remove(authorId: UserId, id: String): RemoveOutcome = inTransaction { connection ->
    connection.acquireRepertoireLock(id)
    val existing = connection.lockLatestVersion(id)
    when {
      existing == null || existing.status == RepertoireStatus.REMOVED -> RemoveOutcome.NotFound
      existing.authorId != authorId -> RemoveOutcome.Forbidden
      else -> {
        connection.updateStatusForEveryVersion(id, RepertoireStatus.REMOVED)
        RemoveOutcome.Removed
      }
    }
  }

  /**
   * Moderation kill switch setting [id]'s status regardless of author. `removed` applies to every
   * version; any other status only to the latest.
   */
  suspend fun setStatus(id: String, status: RepertoireStatus): SetStatusOutcome =
    inTransaction { connection ->
      connection.acquireRepertoireLock(id)
      val existing = connection.lockLatestVersion(id)
      // Removal is a latch: a removed id reads as unknown.
      if (existing == null || existing.status == RepertoireStatus.REMOVED) {
        return@inTransaction SetStatusOutcome.NotFound
      }
      if (status == RepertoireStatus.REMOVED) {
        connection.updateStatusForEveryVersion(id, RepertoireStatus.REMOVED)
      } else {
        connection.updateStatus(id, existing.version, status)
      }
      SetStatusOutcome.Updated(existing.copy(status = status))
    }

  /** The latest version of [id], or `null` when unknown or its latest version was removed. */
  suspend fun get(id: String): RepertoireRow? =
    withContext(ioDispatcher) {
      dataSource.connection.use { connection ->
        connection.latestVersion(id)?.takeUnless { it.status == RepertoireStatus.REMOVED }
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
        if (status == null || status == RepertoireStatus.REMOVED) null else connection.movesOf(id, version)
      }
    }

  /**
   * Records one more anonymous install of [id]. Keyed by id alone, so a republish does not reset
   * the count.
   */
  suspend fun recordInstall(id: String) {
    withContext(ioDispatcher) {
      dataSource.connection.use { connection -> connection.incrementInstallCount(id) }
    }
  }

  /** The install count of every distinct id in [ids], `0` for one never installed. */
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

/**
 * Serializes publish, remove and status changes for [id] until the transaction ends. READ COMMITTED
 * alone would let two transactions compute the same next version.
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
private fun Connection.authorFootprint(authorId: UserId, excludingId: String): Pair<Int, Int> =
  prepareStatement(
      "SELECT count(*), COALESCE(sum(move_count), 0) FROM (" +
        "SELECT DISTINCT ON (id) id, move_count, status FROM repertoire_version " +
        "WHERE author_id = ? ORDER BY id, version DESC" +
        ") latest WHERE status != ? AND id != ?"
    )
    .use { statement ->
      statement.setString(1, authorId.value)
      statement.setString(2, RepertoireStatus.REMOVED.dbValue)
      statement.setString(3, excludingId)
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getInt(1) to rows.getInt(2)
      }
    }

/** Atomically increments [id]'s install count, starting at 1. */
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
private fun Connection.updateStatusForEveryVersion(id: String, status: RepertoireStatus) {
  prepareStatement("UPDATE repertoire_version SET status = ? WHERE id = ?").use { statement ->
    statement.setString(1, status.dbValue)
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
      statement.setString(3, row.authorId.value)
      statement.setString(4, row.title)
      statement.setString(5, row.description)
      statement.setString(6, row.side.dbValue())
      statement.setInt(7, row.moveCount)
      statement.setString(8, row.status.dbValue)
      statement.setTimestamp(9, row.publishedAt.toSqlTimestamp())
      statement.executeUpdate()
    }
}

/** Interns [moves] into `move_edge` and records each one, with its marking, as part of [row]. */
private fun Connection.insertMoves(row: RepertoireRow, moves: List<RepertoireMove>) {
  val edgeIds = resolveEdgeIds(moves.map { EdgeIdentity(it.from, it.to, it.move) })
  prepareStatement(
      "INSERT INTO repertoire_edge (repertoire_id, version, edge_id, move, is_good) " +
        "VALUES (?, ?, ?, ?, ?)"
    )
    .use { statement ->
      for (move in moves) {
        statement.setString(1, row.id)
        statement.setInt(2, row.version)
        statement.setLong(3, edgeIds.getValue(EdgeIdentity(move.from, move.to, move.move)))
        statement.setString(4, move.move)
        statement.setBoolean(5, move.isGood)
        statement.addBatch()
      }
      statement.executeBatch()
    }
}

/** The status of [id]'s [version], or `null` when that version does not exist. */
private fun Connection.statusOf(id: String, version: Int): RepertoireStatus? =
  prepareStatement("SELECT status FROM repertoire_version WHERE id = ? AND version = ?").use {
    statement ->
    statement.setString(1, id)
    statement.setInt(2, version)
    statement.executeQuery().use { rows -> if (rows.next()) RepertoireStatus.fromDb(rows.getString(1)) else null }
  }

/** Every move of [id]'s [version], in a stable order. */
private fun Connection.movesOf(id: String, version: Int): List<RepertoireMove> =
  prepareStatement(
      "SELECT o.position_key, r.move, d.position_key, r.is_good FROM repertoire_edge r " +
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

private fun Connection.updateStatus(id: String, version: Int, status: RepertoireStatus) {
  prepareStatement("UPDATE repertoire_version SET status = ? WHERE id = ? AND version = ?").use {
    statement ->
    statement.setString(1, status.dbValue)
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
    statement.setString(index++, RepertoireStatus.PUBLISHED.dbValue)
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
    authorId = UserId(getString(3)),
    title = getString(4),
    description = getString(5),
    side = sideFromDb(getString(6)),
    moveCount = getInt(7),
    status = RepertoireStatus.fromDb(getString(8)),
    publishedAt = getTimestamp(9).toInstant().toKotlinInstant(),
  )

/** How [this] side is spelled in `repertoire_version.side`. */
private fun RepertoireColor.dbValue(): String =
  when (this) {
    RepertoireColor.WHITE -> "white"
    RepertoireColor.BLACK -> "black"
  }

/**
 * The side [dbValue] names.
 *
 * @throws IllegalStateException for any other spelling.
 */
private fun sideFromDb(dbValue: String): RepertoireColor =
  RepertoireColor.entries.firstOrNull { it.dbValue() == dbValue } ?: error("unknown side '$dbValue'")

/** Converts at full precision; a millisecond round trip would shift a `timestamptz` value. */
private fun Instant.toSqlTimestamp(): Timestamp =
  Timestamp.from(java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()))

private fun java.time.Instant.toKotlinInstant(): Instant =
  Instant.fromEpochSeconds(epochSecond, nano.toLong())
