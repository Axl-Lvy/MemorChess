package proj.memorchess.axl.core.sync

import kotlin.time.Instant

/**
 * In memory stand in for the server, holding settings only. Pulls are driven by a revision counter,
 * like a Postgres sequence, never by a timestamp.
 */
internal class FakeServer {

  private data class Stored(val row: SettingSyncRow, val revision: Long)

  private val rows = mutableMapOf<String, Stored>()
  private var revisionCounter = 0L

  /** Rows whose revision is greater than [since], oldest first. */
  internal fun pull(since: Long): List<SettingSyncRow> =
    rows.values.filter { it.revision > since }.sortedBy { it.revision }.map { it.row }

  /** Highest revision assigned so far, which a client stores as its cursor. */
  internal fun currentRevision(): Long = revisionCounter

  /**
   * Applies [incoming] under last write wins and returns whatever it refused. Never rewrites
   * [SyncRow.updatedAt]; see [isTooFarAhead] for why a rewrite diverges permanently.
   */
  internal fun push(incoming: List<SettingSyncRow>, serverNow: Instant): List<RejectedRow> {
    val rejected = mutableListOf<RejectedRow>()
    for (row in incoming.sortedBy { it.key }) {
      if (row.isTooFarAhead(serverNow)) {
        rejected +=
          RejectedRow(
            kind = "setting",
            id = row.key,
            code = RejectionCode.CLOCK_TOO_FAR_AHEAD,
            reason = "updatedAt is further ahead of server time than the tolerance allows",
          )
        continue
      }
      val stored = rows[row.key]?.row
      val winner = resolve(local = stored, remote = row)
      if (winner.source == ResolutionSource.REMOTE) {
        rows[row.key] = Stored(winner.row, ++revisionCounter)
      } else if (winner.row != row) {
        // The pushed row lost: still advance the survivor's revision, or the pusher, whose cursor
        // has passed it, would never be served it again and diverge permanently.
        rows[row.key] = Stored(winner.row, ++revisionCounter)
      }
    }
    return rejected
  }

  /** Every row currently held, for end state comparison. */
  internal fun snapshot(): Map<String, SettingSyncRow> = rows.mapValues { it.value.row }
}

/**
 * In memory stand in for one device. Its outbox holds keys, not rows, so it never goes stale and
 * repeated edits to one key collapse into a single push.
 */
internal class FakeClient(deviceId: String) {

  private val deviceId = DeviceId(deviceId)

  private val rows = mutableMapOf<String, SettingSyncRow>()
  private val dirty = mutableSetOf<String>()
  private var cursor = 0L

  /** Incremented on every local write, including a re-stamp, so no two versions ever tie. */
  private var writeSeq = 0L

  /** Writes a value locally and marks its key dirty. */
  internal fun edit(key: String, value: String, at: Instant) {
    rows[key] =
      SettingSyncRow(
        key = key,
        value = value,
        isDeleted = false,
        updatedAt = at,
        originDevice = deviceId,
        deviceSeq = ++writeSeq,
      )
    dirty += key
  }

  /** Tombstones a key locally and marks it dirty. Does nothing when the key is unknown. */
  internal fun delete(key: String, at: Instant) {
    val existing = rows[key] ?: return
    rows[key] =
      existing.copy(
        isDeleted = true,
        updatedAt = at,
        originDevice = deviceId,
        deviceSeq = ++writeSeq,
      )
    dirty += key
  }

  /**
   * Pushes every dirty key, re-stamps clock refusals for the next round rather than retrying now,
   * then pulls everything new.
   */
  internal fun sync(server: FakeServer, serverNow: Instant) {
    val rejected = server.push(dirty.mapNotNull { rows[it] }, serverNow)
    dirty.clear()
    for (rejection in rejected) {
      if (rejection.code != RejectionCode.CLOCK_TOO_FAR_AHEAD) continue
      val stale = rows[rejection.id] ?: continue
      // A re-stamp is a new version, so it takes a new sequence number. Two refused writes to one
      // key both re-stamp to serverNow, and the sequence is the only thing separating them.
      rows[rejection.id] = stale.copy(updatedAt = serverNow, deviceSeq = ++writeSeq)
      dirty += rejection.id
    }
    for (incoming in server.pull(cursor)) {
      val winner = resolve(local = rows[incoming.key], remote = incoming)
      if (winner.source == ResolutionSource.REMOTE) {
        rows[incoming.key] = winner.row
      }
    }
    cursor = server.currentRevision()
  }

  /** Every row currently held, for end state comparison. */
  internal fun snapshot(): Map<String, SettingSyncRow> = rows.toMap()

  /** Rows a user would actually see, tombstones excluded. */
  internal fun visible(): Map<String, String> =
    rows.filterValues { !it.isDeleted }.mapValues { it.value.value }
}
