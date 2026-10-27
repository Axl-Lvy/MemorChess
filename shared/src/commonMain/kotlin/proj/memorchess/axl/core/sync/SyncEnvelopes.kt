package proj.memorchess.axl.core.sync

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The JSON configuration both sides of the protocol must use. `ignoreUnknownKeys` keeps a shipped
 * peer working when a newer one adds a field.
 */
val SYNC_JSON: Json = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

/**
 * One bounded page of rows the caller has not seen.
 *
 * @property serverTime The server's clock at response time, so a client can detect its own skew.
 * @property nextCursor Revision the server withheld rows above, or `null` when no table filled its
 *   page. Diagnostic only: the caller's position is kept server side.
 * @property pageToken Sent back as `ack` on the next pull once these rows are written locally.
 */
@Serializable
data class SyncPullResponse(
  val serverTime: Instant,
  val nextCursor: Revision?,
  val pageToken: String,
  val nodes: List<NodeSyncRow>,
  val edges: List<EdgeSyncRow>,
  val settings: List<SettingSyncRow>,
  val repertoires: List<RepertoireSyncRow> = emptyList(),
  val tags: List<EdgeRepertoireTagSyncRow> = emptyList(),
)

/**
 * A batch of locally changed rows, sent in one request.
 *
 * @property device The pushing device's origin id. Deliberately without a default: a defaulted
 *   empty string would turn the server's refusal into a silent failure to sync.
 */
@Serializable
data class SyncPushRequest(
  val nodes: List<NodeSyncRow>,
  val edges: List<EdgeSyncRow>,
  val settings: List<SettingSyncRow>,
  val repertoires: List<RepertoireSyncRow> = emptyList(),
  val tags: List<EdgeRepertoireTagSyncRow> = emptyList(),
  val device: DeviceId,
)

/**
 * The outcome of a push.
 *
 * @property revision Highest revision the server assigned while applying this batch.
 * @property rejected Rows the server refused. A client surfaces these rather than retrying forever.
 */
@Serializable
data class SyncPushResponse(
  val serverTime: Instant,
  val revision: Revision,
  val rejected: List<RejectedRow>,
)

/**
 * One refused row.
 *
 * @property kind Resource name: `node`, `edge`, `setting`, `repertoire` or `tag`.
 * @property code One of [RejectionCode]. Clients branch on this, never on [reason].
 * @property reason Human readable explanation, safe to log and to show.
 */
@Serializable
data class RejectedRow(
  val kind: String,
  val id: String,
  val code: String,
  val reason: String,
)

/**
 * Causes a row can be refused for. Plain strings rather than an enum, so an older client survives a
 * code it has never heard of.
 */
object RejectionCode {

  /**
   * The row's [SyncRow.updatedAt] was further ahead of server time than [SYNC_SKEW_TOLERANCE]
   * allows. The client re-stamps against the response's `serverTime` and retries.
   */
  const val CLOCK_TOO_FAR_AHEAD: String = "clock_too_far_ahead"
}
