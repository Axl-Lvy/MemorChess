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
 */
internal class RepertoireStore(
  private val dataSource: DataSource,
  private val blobs: RepertoireBlobStore,
  private val maxPayloadBytes: Int = MAX_REPERTOIRE_PAYLOAD_BYTES,
  private val maxMoves: Int = MAX_REPERTOIRE_MOVES,
  private val maxRepertoiresPerUser: Int = MAX_REPERTOIRES_PER_USER,
  private val maxTotalPayloadBytesPerUser: Long = MAX_TOTAL_PAYLOAD_BYTES_PER_USER,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
    val validation =
      withContext(ioDispatcher) { RepertoirePgnValidator.validate(pgn, maxPayloadBytes, maxMoves) }
    return when (validation) {
      is RepertoireValidation.Rejected -> PublishOutcome.InvalidPayload(validation.reason)
      is RepertoireValidation.TooLarge -> PublishOutcome.PayloadTooLarge(validation.reason)
      is RepertoireValidation.Valid ->
        doPublish(authorId, id, title, description, side, pgn, validation.moveCount, now)
    }
  }

  /**
   * Inserts the row, then stores the blob. Row first so a caller can never observe a committed
   * version whose blob might still be missing because of a rejection below it. If [blobs].put fails
   * after the row commits, the row is deleted again so a repertoire never outlives its payload.
   */
  private suspend fun doPublish(
    authorId: String,
    id: String,
    title: String,
    description: String,
    side: String,
    pgn: String,
    moveCount: Int,
    now: Instant,
  ): PublishOutcome {
    val payloadBytes = pgn.encodeToByteArray()
    val sha256 = payloadBytes.sha256Hex()

    val outcome = inTransaction { connection ->
      val existing = connection.lockLatestVersion(id)
      if (existing != null && existing.authorId != authorId) {
        return@inTransaction PublishOutcome.Forbidden
      }

      val (otherCount, otherBytes) = connection.authorFootprint(authorId, excludingId = id)
      if (existing == null && otherCount >= maxRepertoiresPerUser) {
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
   * Marks [id]'s latest version as removed, deleting its blob when no surviving version still
   * references the same payload hash.
   */
  suspend fun remove(authorId: String, id: String): RemoveOutcome {
    val outcome = inTransaction { connection ->
      val existing = connection.lockLatestVersion(id)
      when {
        existing == null || existing.status == STATUS_REMOVED -> RemoveTxOutcome.NotFound
        existing.authorId != authorId -> RemoveTxOutcome.Forbidden
        else -> {
          connection.updateStatus(id, existing.version, STATUS_REMOVED)
          val stillReferenced = connection.blobStillReferenced(existing.payloadSha256)
          RemoveTxOutcome.Removed(existing.payloadSha256, deleteBlob = !stillReferenced)
        }
      }
    }
    return when (outcome) {
      is RemoveTxOutcome.Removed -> {
        if (outcome.deleteBlob) blobs.delete(outcome.sha256)
        RemoveOutcome.Removed
      }
      RemoveTxOutcome.Forbidden -> RemoveOutcome.Forbidden
      RemoveTxOutcome.NotFound -> RemoveOutcome.NotFound
    }
  }

  /**
   * Moderation kill switch: sets [id]'s latest version to [status] regardless of author, deleting
   * its blob under the same reference counting rule as [remove] when [status] is `removed`.
   */
  suspend fun setStatus(id: String, status: String): SetStatusOutcome {
    val outcome = inTransaction { connection ->
      val existing = connection.lockLatestVersion(id)
      // A removed repertoire's blob is already gone, so moving it back to published or unlisted
      // would publish a broken pgn link. Treated the same as an unknown id rather than as a
      // resurrection the moderator has to know to avoid.
      if (existing == null || existing.status == STATUS_REMOVED) {
        return@inTransaction SetStatusTxOutcome.NotFound
      }
      connection.updateStatus(id, existing.version, status)
      val deleteBlob =
        status == STATUS_REMOVED && !connection.blobStillReferenced(existing.payloadSha256)
      SetStatusTxOutcome.Updated(existing.copy(status = status), existing.payloadSha256, deleteBlob)
    }
    return when (outcome) {
      is SetStatusTxOutcome.Updated -> {
        if (outcome.deleteBlob) blobs.delete(outcome.sha256)
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

  /** The stored payload bytes for [sha256], or `null` when nothing is stored there. */
  suspend fun readPayload(sha256: String): ByteArray? = blobs.get(sha256)

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

  private fun idProblem(id: String): String? =
    when {
      id.length < MIN_ID_LENGTH || id.length > MAX_ID_LENGTH ->
        "id must be $MIN_ID_LENGTH to $MAX_ID_LENGTH characters, was ${id.length}"
      !ID_PATTERN.matches(id) ->
        "id must be lowercase letters, digits and single hyphens, was '$id'"
      else -> null
    }
}

private const val STATUS_REMOVED = "removed"

/** Outcome of the [RepertoireStore.remove] transaction, before the blob is deleted. */
private sealed class RemoveTxOutcome {
  data class Removed(val sha256: String, val deleteBlob: Boolean) : RemoveTxOutcome()

  data object NotFound : RemoveTxOutcome()

  data object Forbidden : RemoveTxOutcome()
}

/** Outcome of the [RepertoireStore.setStatus] transaction, before the blob is deleted. */
private sealed class SetStatusTxOutcome {
  data class Updated(val row: RepertoireRow, val sha256: String, val deleteBlob: Boolean) :
    SetStatusTxOutcome()

  data object NotFound : SetStatusTxOutcome()
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
