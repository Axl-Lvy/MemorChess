package proj.memorchess.axl.server.sync

import kotlin.time.Instant
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.ResolutionSource
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.resolve

/**
 * A client stand in that drives the real [SyncStore].
 *
 * This is a **re-implementation** of the client half, not a reuse: `:shared`'s convergence fakes
 * are `internal` to that module and invisible here. That is the point of the exercise. The property
 * is what carries over, so if the SQL disagrees with the semantics `:shared` proved, the
 * convergence test fails.
 *
 * Settings only, which is enough to exercise the store's ordering and its cursor.
 *
 * @property deviceId Opaque device id, which also breaks conflict ties.
 */
internal class TestDevice(private val deviceId: String) {

  private val rows = mutableMapOf<String, SettingSyncRow>()
  private val dirty = mutableSetOf<String>()
  private var cursor = 0L
  private var writeSeq = 0L

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

  /** Pushes the dirty rows, re-stamps whatever was refused for skew, then pulls and applies. */
  internal suspend fun sync(transport: SyncTransport, serverNow: Instant) {
    val outgoing = dirty.mapNotNull { rows[it] }
    val response = transport.push(SyncPushRequest(emptyList(), emptyList(), outgoing), serverNow)
    dirty.clear()
    for (rejection in response.rejected) {
      if (rejection.code != RejectionCode.CLOCK_TOO_FAR_AHEAD) continue
      val stale = rows[rejection.id] ?: continue
      // A re-stamp is a new version, so it takes a new sequence number.
      rows[rejection.id] = stale.copy(updatedAt = serverNow, deviceSeq = ++writeSeq)
      dirty += rejection.id
    }

    var guard = 0
    while (true) {
      val page = transport.pull(cursor, 100, serverNow)
      for (incoming in page.settings) {
        val winner = resolve(local = rows[incoming.key], remote = incoming)
        if (winner.source == ResolutionSource.REMOTE) rows[incoming.key] = winner.row
      }
      cursor = page.nextCursor ?: break
      if (guard++ > 100) error("paging did not terminate")
    }
  }

  internal fun snapshot(): Map<String, SettingSyncRow> = rows.toMap()

  internal fun visible(): Map<String, String> =
    rows.filterValues { !it.isDeleted }.mapValues { it.value.value }
}
