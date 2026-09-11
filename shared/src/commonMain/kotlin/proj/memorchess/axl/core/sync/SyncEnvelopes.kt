package proj.memorchess.axl.core.sync

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The JSON configuration both sides of the protocol must use.
 *
 * `ignoreUnknownKeys` is the forward compatibility guarantee: a newer peer may add a field, and an
 * already shipped one must keep working. `encodeDefaults` is on so a default valued field is always
 * present on the wire, which keeps a payload readable in a log or a test failure.
 */
val SYNC_JSON: Json = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

/**
 * One bounded page of rows the caller has not seen.
 *
 * @property serverTime The server's clock at the moment of the response, so a client can detect its
 *   own skew.
 * @property nextCursor Revision the server withheld rows above, or `null` when no table filled its
 *   page. Diagnostic only: the caller's position is kept server side.
 * @property pageToken Opaque token naming this page. The caller sends it back as `ack` on its next
 *   pull once these rows are written locally, which is how the server learns the page landed.
 * @property nodes Changed positions.
 * @property edges Changed moves.
 * @property settings Changed settings.
 * @property repertoires Changed repertoires.
 * @property tags Changed edge to repertoire tags.
 */
@Serializable
data class SyncPullResponse(
  val serverTime: Instant,
  val nextCursor: Long?,
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
 * @property nodes Positions to write.
 * @property edges Moves to write.
 * @property settings Settings to write.
 * @property repertoires Repertoires to write.
 * @property tags Edge to repertoire tags to write.
 * @property device The pushing device's origin id. Deliberately without a default, so that no call
 *   site and no wire payload can omit it: a push that names no device is refused, and a defaulted
 *   empty string would turn that refusal into a silent failure to sync at all.
 */
@Serializable
data class SyncPushRequest(
  val nodes: List<NodeSyncRow>,
  val edges: List<EdgeSyncRow>,
  val settings: List<SettingSyncRow>,
  val repertoires: List<RepertoireSyncRow> = emptyList(),
  val tags: List<EdgeRepertoireTagSyncRow> = emptyList(),
  val device: String,
)

/**
 * The outcome of a push.
 *
 * @property serverTime The server's clock at the moment of the response.
 * @property revision Highest revision the server assigned while applying this batch.
 * @property rejected Rows the server refused. A client surfaces these rather than retrying forever.
 */
@Serializable
data class SyncPushResponse(
  val serverTime: Instant,
  val revision: Long,
  val rejected: List<RejectedRow>,
)

/**
 * One refused row.
 *
 * @property kind Resource name: `node`, `edge`, `setting`, `repertoire` or `tag`.
 * @property id Identifier of the row within its resource, for the client to match against its own.
 * @property code Machine readable cause, one of [RejectionCode]. Clients branch on this, never on
 *   [reason].
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
 * Causes a row can be refused for.
 *
 * These are plain strings rather than an enum on purpose: an older client must survive a newer
 * server sending a code it has never heard of, which an enum would turn into a decoding failure.
 */
object RejectionCode {

  /**
   * The row's [SyncRow.updatedAt] was further ahead of server time than [SYNC_SKEW_TOLERANCE]
   * allows. The client re-stamps against the response's `serverTime` and retries.
   */
  const val CLOCK_TOO_FAR_AHEAD: String = "clock_too_far_ahead"

  /**
   * An [proj.memorchess.axl.core.sync.EdgeRepertoireTagSyncRow] named an edge the server has no
   * record of, neither already stored nor in the same push's own edges. The client pushes the edge
   * itself first, then retries the tag.
   */
  const val EDGE_NOT_FOUND: String = "edge_not_found"
}
