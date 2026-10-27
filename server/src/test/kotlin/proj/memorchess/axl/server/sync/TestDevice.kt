package proj.memorchess.axl.server.sync

import kotlin.time.Instant
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.ResolutionSource
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.resolve

/**
 * A settings only client stand in driving the real [SyncStore]. It re-implements the client half,
 * so SQL that disagrees with the semantics `:shared` proved fails the convergence test.
 *
 * @property deviceId Opaque device id, which also breaks conflict ties.
 */
internal class TestDevice(private val deviceId: String) {

  private val rows = mutableMapOf<String, SettingSyncRow>()
  private val dirty = mutableSetOf<String>()
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
    val response =
      transport.push(
        SyncPushRequest(emptyList(), emptyList(), outgoing, device = deviceId),
        serverNow,
      )
    dirty.clear()
    for (rejection in response.rejected) {
      if (rejection.code != RejectionCode.CLOCK_TOO_FAR_AHEAD) continue
      val stale = rows[rejection.id] ?: continue
      // A re-stamp is a new version, so it takes a new sequence number.
      rows[rejection.id] = stale.copy(updatedAt = serverNow, deviceSeq = ++writeSeq)
      dirty += rejection.id
    }

    var guard = 0
    var ack: String? = null
    while (true) {
      val page = transport.pull(ack, 100, serverNow)
      if (page.settings.isEmpty()) break
      for (incoming in page.settings) {
        val winner = resolve(local = rows[incoming.key], remote = incoming)
        if (winner.source == ResolutionSource.REMOTE) rows[incoming.key] = winner.row
      }
      ack = page.pageToken
      if (guard++ > 100) error("paging did not terminate")
    }
  }

  internal fun snapshot(): Map<String, SettingSyncRow> = rows.toMap()

  internal fun visible(): Map<String, String> =
    rows.filterValues { !it.isDeleted }.mapValues { it.value.value }
}
