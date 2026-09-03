package proj.memorchess.axl.server.sync

import java.sql.Connection
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import proj.memorchess.axl.core.sync.RejectedRow
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.ResolutionSource
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncPushResponse
import proj.memorchess.axl.core.sync.isTooFarAhead
import proj.memorchess.axl.core.sync.resolve

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

      val acceptedSettings =
        request.settings.filter { row ->
          val refuse = row.isTooFarAhead(serverNow)
          if (refuse) rejected += clockRefusal("setting", row.key)
          !refuse
        }

      dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
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
