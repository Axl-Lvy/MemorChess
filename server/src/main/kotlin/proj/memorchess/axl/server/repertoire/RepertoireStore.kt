package proj.memorchess.axl.server.repertoire

import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Largest payload accepted for one repertoire version, checked before parsing. */
internal const val MAX_REPERTOIRE_PAYLOAD_BYTES: Int = 512 * 1024

/** Largest number of distinct `(position, move)` edges accepted in one repertoire. */
internal const val MAX_REPERTOIRE_MOVES: Int = 5_000

/** Largest number of non removed repertoires one author may own at once. */
internal const val MAX_REPERTOIRES_PER_USER: Int = 20

/** Largest total payload size, summed across an author's non removed repertoires. */
internal const val MAX_TOTAL_PAYLOAD_BYTES_PER_USER: Long = 5L * 1024 * 1024

/** Largest number of rows [RepertoireStore.allPublished] will ever return. */
private const val MAX_MANIFEST_ROWS: Int = 10_000

/** Shape a repertoire id (a catalog slug) must match. */
private val ID_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

/**
 * Shape a stored payload's sha256 must match: 64 lowercase hex characters. Checked before the hash
 * is used in a database lookup or an S3 key, so a path segment carrying something else (for example
 * a decoded `/` aimed at another key in the bucket) is rejected outright rather than looked up.
 */
private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

private const val MIN_ID_LENGTH = 3
private const val MAX_ID_LENGTH = 64

/** Longest title accepted. It is shown in the public catalog, so an unbounded one is a footgun. */
private const val MAX_TITLE_LENGTH = 200

/** Longest description accepted, for the same reason as [MAX_TITLE_LENGTH]. */
private const val MAX_DESCRIPTION_LENGTH = 2_000

/** One stored version of a repertoire: exactly one row of `repertoire_version`. */
internal data class RepertoireRow(
  val id: String,
  val version: Int,
  val authorId: String,
  val title: String,
  val description: String,
  val side: String,
  val payloadSha256: String,
  val payloadBytes: Int,
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
  val pgn: String,
)

/** Outcome of [RepertoireStore.publish]. */
internal sealed class PublishOutcome {
  /** The version was stored. */
  data class Published(val row: RepertoireRow) : PublishOutcome()

  /**
   * The payload does not parse, has no playable move, plays an illegal move, or the id/side is
   * malformed.
   */
  data class InvalidPayload(val reason: String) : PublishOutcome()

  /** The payload or its move count exceeds a server side cap. */
  data class PayloadTooLarge(val reason: String) : PublishOutcome()

  /** The id already belongs to a different author. */
  data object Forbidden : PublishOutcome()

  /** The id's latest version was removed by a moderator. Republishing over that is refused. */
  data object Removed : PublishOutcome()

  /** Publishing would exceed a per author quota. */
  data class QuotaExceeded(val reason: String) : PublishOutcome()

  /** Validation failed for a reason that was not the caller's fault. Logged on the server side. */
  data class Failed(val reason: String) : PublishOutcome()
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
 * The server side of published repertoires, over a Postgres database and a [RepertoireBlobStore].
 *
 * A repertoire is content addressed: the metadata row commits first, then [RepertoireBlobStore.put]
 * stores the payload it references. A publish rejected by validation or a quota never reaches
 * either write. A blob write that fails after the row commits is compensated by deleting that row
 * again, so a repertoire never outlives its payload. Every lookup reads the **latest version** of
 * an id and its status. An older version's status is never consulted once a newer one exists.
 *
 * @param dataSource Pooled connections. Each call takes one and returns it.
 * @param blobs Payload storage, keyed by sha256.
 * @param maxPayloadBytes Cap on one publish's payload size.
 * @param maxMoves Cap on one publish's distinct move count.
 * @param maxRepertoiresPerUser Cap on how many non removed repertoires one author may own.
 * @param maxTotalPayloadBytesPerUser Cap on the summed payload size of one author's non removed
 *   repertoires.
 * @param ioDispatcher Where the blocking JDBC work runs. Injected so a caller can substitute one.
 * @param validate Validates a payload before it is stored. Injected so a test can substitute one
 *   without going through the real parser, the same way [ioDispatcher] is substituted.
 */
internal class RepertoireStore(
  private val dataSource: DataSource,
  private val blobs: RepertoireBlobStore,
  private val maxPayloadBytes: Int = MAX_REPERTOIRE_PAYLOAD_BYTES,
  private val maxMoves: Int = MAX_REPERTOIRE_MOVES,
  private val maxRepertoiresPerUser: Int = MAX_REPERTOIRES_PER_USER,
  private val maxTotalPayloadBytesPerUser: Long = MAX_TOTAL_PAYLOAD_BYTES_PER_USER,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val validate: (String, Int, Int) -> RepertoireValidation =
    RepertoirePgnValidator::validate,
) {

  /**
   * Validates and stores a new version of [id], authored by [authorId].
   *
   * The payload is validated with [RepertoirePgnValidator] before anything is written. A first
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
    pgn: String,
    now: Instant,
  ): PublishOutcome {
    idProblem(id)?.let {
      return PublishOutcome.InvalidPayload(it)
    }
    if (side != "white" && side != "black") {
      return PublishOutcome.InvalidPayload("side must be 'white' or 'black', was '$side'")
    }
    if (title.length > MAX_TITLE_LENGTH) {
      return PublishOutcome.InvalidPayload(
        "title must be at most $MAX_TITLE_LENGTH characters, was ${title.length}"
      )
    }
    if (description.length > MAX_DESCRIPTION_LENGTH) {
      return PublishOutcome.InvalidPayload(
        "description must be at most $MAX_DESCRIPTION_LENGTH characters, was ${description.length}"
      )
    }

    // RepertoirePgnValidator blocks its caller on a worker thread it manages itself (see its own
    // KDoc), so this runs on ioDispatcher rather than whatever dispatcher the route handler is on.
    val validation = withContext(ioDispatcher) { validate(pgn, maxPayloadBytes, maxMoves) }
    return when (validation) {
      is RepertoireValidation.Rejected -> PublishOutcome.InvalidPayload(validation.reason)
      is RepertoireValidation.TooLarge -> PublishOutcome.PayloadTooLarge(validation.reason)
      is RepertoireValidation.Failed -> PublishOutcome.Failed(validation.reason)
      is RepertoireValidation.Valid ->
        doPublish(
          authorId,
          PublishInput(id, title, description, side, pgn),
          validation.moveCount,
          now,
        )
    }
  }

  /**
   * Inserts the row, then stores the blob. Row first so a caller can never observe a committed
   * version whose blob might still be missing because of a rejection below it. If [blobs].put fails
   * after the row commits, the row is deleted again so a repertoire never outlives its payload.
   *
   * The transaction holds [acquireRepertoireLock] on [input]'s id for its whole duration, so two
   * concurrent publishes (or a double click) for the same id never compute the same next version
   * number.
   */
  private suspend fun doPublish(
    authorId: String,
    input: PublishInput,
    moveCount: Int,
    now: Instant,
  ): PublishOutcome {
    val (id, title, description, side, pgn) = input
    val payloadBytes = pgn.encodeToByteArray()
    val sha256 = payloadBytes.sha256Hex()

    val outcome = inTransaction { connection ->
      connection.acquireRepertoireLock(id)
      val existing = connection.lockLatestVersion(id)
      if (existing != null && existing.authorId != authorId) {
        return@inTransaction PublishOutcome.Forbidden
      }

      // A removed id counts against the quota the same as a brand new one: without this, an
      // author at the cap can free a slot by removing a repertoire and then republish the same id
      // to occupy it again, over and over, never actually reducing their footprint.
      val republishingRemoved = existing != null && existing.status == STATUS_REMOVED
      val (otherCount, otherBytes) = connection.authorFootprint(authorId, excludingId = id)
      if ((existing == null || republishingRemoved) && otherCount >= maxRepertoiresPerUser) {
        return@inTransaction PublishOutcome.QuotaExceeded(
          "you already own $otherCount repertoires, the cap is $maxRepertoiresPerUser"
        )
      }
      val projectedBytes = otherBytes + payloadBytes.size
      if (projectedBytes > maxTotalPayloadBytesPerUser) {
        return@inTransaction PublishOutcome.QuotaExceeded(
          "publishing would use $projectedBytes of your $maxTotalPayloadBytesPerUser byte quota"
        )
      }
      // A moderator's removal is a latch, not a mutable flag an author can clear by republishing.
      // setStatus refuses the same reversal from the moderation side; this is its publish side
      // counterpart.
      if (republishingRemoved) {
        return@inTransaction PublishOutcome.Removed
      }

      val version = (existing?.version ?: 0) + 1
      val row =
        RepertoireRow(
          id = id,
          version = version,
          authorId = authorId,
          title = title,
          description = description,
          side = side,
          payloadSha256 = sha256,
          payloadBytes = payloadBytes.size,
          moveCount = moveCount,
          status = "published",
          publishedAt = now,
        )
      connection.insertVersion(row)
      PublishOutcome.Published(row)
    }

    if (outcome is PublishOutcome.Published) {
      // TODO: this compensates a blob write failure on a best effort basis, but a process death
      // between the row commit above and the delete below still leaves an orphan row with no
      // blob. A three phase publish (insert as pending, put the blob, then flip to published)
      // would close that gap. Tracked as a follow up, not done here.
      try {
        blobs.put(sha256, payloadBytes)
      } catch (e: Exception) {
        deleteVersion(outcome.row.id, outcome.row.version)
        throw e
      }
    }
    return outcome
  }

  /**
   * Removes one exact version row. Used only to compensate a blob write that failed after commit.
   */
  private suspend fun deleteVersion(id: String, version: Int) {
    withContext(ioDispatcher) {
      dataSource.connection.use { connection ->
        connection
          .prepareStatement("DELETE FROM repertoire_version WHERE id = ? AND version = ?")
          .use { statement ->
            statement.setString(1, id)
            statement.setInt(2, version)
            statement.executeUpdate()
          }
      }
    }
  }

  /**
   * Marks every version of [id] as removed, deleting each one's blob that no surviving version of
   * any id still references.
   *
   * Every version, not only the latest, so a hash [readPayload] used to serve stops being "live" by
   * the same definition it checks: a hash is live only while some non removed row references it.
   * Without cascading to superseded versions, an old version's row would keep its original
   * `published` status forever and its blob would never be reclaimed, nor would its direct download
   * link ever stop working, even after the id it belongs to is fully taken down.
   */
  suspend fun remove(authorId: String, id: String): RemoveOutcome {
    val outcome = inTransaction { connection ->
      connection.acquireRepertoireLock(id)
      val existing = connection.lockLatestVersion(id)
      when {
        existing == null || existing.status == STATUS_REMOVED -> RemoveTxOutcome.NotFound
        existing.authorId != authorId -> RemoveTxOutcome.Forbidden
        else -> RemoveTxOutcome.Removed(connection.removeAllVersions(id))
      }
    }
    return when (outcome) {
      is RemoveTxOutcome.Removed -> {
        outcome.orphanedHashes.forEach { blobs.delete(it) }
        RemoveOutcome.Removed
      }
      RemoveTxOutcome.Forbidden -> RemoveOutcome.Forbidden
      RemoveTxOutcome.NotFound -> RemoveOutcome.NotFound
    }
  }

  /**
   * Moderation kill switch: sets [id]'s status to [status] regardless of author. When [status] is
   * `removed`, this cascades to every version of [id] and deletes each one's blob under the same
   * reference counting rule as [remove], for the reason explained on [remove]'s KDoc. Any other
   * status only ever touches the latest version, matching [get] and [listPublished], which never
   * consult an older version's status once a newer one exists.
   */
  suspend fun setStatus(id: String, status: String): SetStatusOutcome {
    val outcome = inTransaction { connection ->
      connection.acquireRepertoireLock(id)
      val existing = connection.lockLatestVersion(id)
      // A removed repertoire's blob is already gone, so moving it back to published or unlisted
      // would publish a broken pgn link. Treated the same as an unknown id rather than as a
      // resurrection the moderator has to know to avoid.
      if (existing == null || existing.status == STATUS_REMOVED) {
        return@inTransaction SetStatusTxOutcome.NotFound
      }
      if (status == STATUS_REMOVED) {
        val orphanedHashes = connection.removeAllVersions(id)
        SetStatusTxOutcome.Updated(existing.copy(status = status), orphanedHashes)
      } else {
        connection.updateStatus(id, existing.version, status)
        SetStatusTxOutcome.Updated(existing.copy(status = status), emptyList())
      }
    }
    return when (outcome) {
      is SetStatusTxOutcome.Updated -> {
        outcome.orphanedHashes.forEach { blobs.delete(it) }
        SetStatusOutcome.Updated(outcome.row)
      }
      SetStatusTxOutcome.NotFound -> SetStatusOutcome.NotFound
    }
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
   * The stored payload bytes for [sha256], or `null` when [sha256] is not a well formed sha256
   * digest, nothing is stored there, or no non removed version currently references it.
   *
   * The last case is what makes moderation ([setStatus], [remove]) actually revoke access to the
   * bytes rather than only hiding the id from listings: without it, the content stays downloadable
   * by anyone who already has the link.
   */
  suspend fun readPayload(sha256: String): ByteArray? {
    if (!SHA256_PATTERN.matches(sha256)) return null
    val referenced =
      withContext(ioDispatcher) {
        dataSource.connection.use { connection -> connection.blobStillReferenced(sha256) }
      }
    return if (referenced) blobs.get(sha256) else null
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

/**
 * Describes what is wrong with [id] as a catalog slug, or `null` when it is well formed. Shared
 * with the routing layer so an anonymous write keyed by a caller supplied id (see `recordInstall`'s
 * route) can reject a malformed one before it ever reaches the store.
 */
internal fun idProblem(id: String): String? =
  when {
    id.length < MIN_ID_LENGTH || id.length > MAX_ID_LENGTH ->
      "id must be $MIN_ID_LENGTH to $MAX_ID_LENGTH characters, was ${id.length}"
    !ID_PATTERN.matches(id) -> "id must be lowercase letters, digits and single hyphens, was '$id'"
    else -> null
  }

private const val STATUS_REMOVED = "removed"

/** Outcome of the [RepertoireStore.remove] transaction, before the orphaned blobs are deleted. */
private sealed class RemoveTxOutcome {
  data class Removed(val orphanedHashes: List<String>) : RemoveTxOutcome()

  data object NotFound : RemoveTxOutcome()

  data object Forbidden : RemoveTxOutcome()
}

/**
 * Outcome of the [RepertoireStore.setStatus] transaction, before the orphaned blobs are deleted.
 */
private sealed class SetStatusTxOutcome {
  data class Updated(val row: RepertoireRow, val orphanedHashes: List<String>) :
    SetStatusTxOutcome()

  data object NotFound : SetStatusTxOutcome()
}

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
 * The count and summed payload bytes of [authorId]'s non removed repertoires, one per id (its
 * latest version), excluding [excludingId].
 */
private fun Connection.authorFootprint(authorId: String, excludingId: String): Pair<Int, Long> =
  prepareStatement(
      "SELECT count(*), COALESCE(sum(payload_bytes), 0) FROM (" +
        "SELECT DISTINCT ON (id) id, payload_bytes, status FROM repertoire_version " +
        "WHERE author_id = ? ORDER BY id, version DESC" +
        ") latest WHERE status != ? AND id != ?"
    )
    .use { statement ->
      statement.setString(1, authorId)
      statement.setString(2, STATUS_REMOVED)
      statement.setString(3, excludingId)
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getInt(1) to rows.getLong(2)
      }
    }

/** Whether any non removed version still references [sha256]. */
private fun Connection.blobStillReferenced(sha256: String): Boolean =
  prepareStatement(
      "SELECT count(*) FROM repertoire_version WHERE payload_sha256 = ? AND status != ?"
    )
    .use { statement ->
      statement.setString(1, sha256)
      statement.setString(2, STATUS_REMOVED)
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getLong(1) > 0
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

/**
 * Sets every version of [id], not only its latest, to [STATUS_REMOVED], then returns the distinct
 * payload hashes among them that no other non removed row (of [id] or any other id) still
 * references, and so are safe to delete from [RepertoireBlobStore].
 */
private fun Connection.removeAllVersions(id: String): List<String> {
  val hashes = distinctHashesOf(id)
  updateStatusForEveryVersion(id, STATUS_REMOVED)
  return hashes.filterNot { blobStillReferenced(it) }
}

/** Every distinct payload hash across all of [id]'s versions. */
private fun Connection.distinctHashesOf(id: String): List<String> =
  prepareStatement("SELECT DISTINCT payload_sha256 FROM repertoire_version WHERE id = ?").use {
    statement ->
    statement.setString(1, id)
    statement.executeQuery().use { rows ->
      buildList { while (rows.next()) add(rows.getString(1)) }
    }
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
        "payload_sha256, payload_bytes, move_count, status, published_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    )
    .use { statement ->
      statement.setString(1, row.id)
      statement.setInt(2, row.version)
      statement.setString(3, row.authorId)
      statement.setString(4, row.title)
      statement.setString(5, row.description)
      statement.setString(6, row.side)
      statement.setString(7, row.payloadSha256)
      statement.setInt(8, row.payloadBytes)
      statement.setInt(9, row.moveCount)
      statement.setString(10, row.status)
      statement.setTimestamp(11, row.publishedAt.toSqlTimestamp())
      statement.executeUpdate()
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
  "id, version, author_id, title, description, side, payload_sha256, payload_bytes, " +
    "move_count, status, published_at"

private fun ResultSet.toRow(): RepertoireRow =
  RepertoireRow(
    id = getString(1),
    version = getInt(2),
    authorId = getString(3),
    title = getString(4),
    description = getString(5),
    side = getString(6),
    payloadSha256 = getString(7),
    payloadBytes = getInt(8),
    moveCount = getInt(9),
    status = getString(10),
    publishedAt = getTimestamp(11).toInstant().toKotlinInstant(),
  )

private fun ByteArray.sha256Hex(): String =
  MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

/**
 * Converts without losing precision, duplicated from `sync/SyncStore.kt` rather than shared with
 * it: `kotlin.time.Instant` carries nanoseconds and `timestamptz` carries microseconds, so a
 * millisecond round trip would silently move a timestamp.
 */
private fun Instant.toSqlTimestamp(): Timestamp =
  Timestamp.from(java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()))

private fun java.time.Instant.toKotlinInstant(): Instant =
  Instant.fromEpochSeconds(epochSecond, nano.toLong())
