package proj.memorchess.axl.core.sync

import kotlinx.serialization.Serializable

/**
 * The body of every response the server does not answer with a success payload.
 *
 * @property code Machine readable cause, one of [ApiErrorCode]. Clients branch on this, never on
 *   [message].
 * @property message Human readable explanation, safe to log and to show.
 */
@Serializable data class ApiError(val code: String, val message: String)

/**
 * Causes a request can fail for.
 *
 * Plain strings for the same reason as [RejectionCode]: an older client must survive a newer server
 * sending a code it has never heard of, which an enum would turn into a decoding failure.
 */
object ApiErrorCode {

  /** The request was malformed: unparseable body, or a query parameter outside its domain. */
  const val BAD_REQUEST: String = "bad_request"

  /** No bearer token, or one that failed verification. */
  const val UNAUTHORIZED: String = "unauthorized"

  /** The request body, or the batch inside it, exceeded the server's cap. */
  const val TOO_LARGE: String = "too_large"

  /** The server failed for a reason the caller cannot act on. Details stay in the server log. */
  const val INTERNAL: String = "internal"

  /** The caller is authenticated but is not the author of the resource. */
  const val FORBIDDEN: String = "forbidden"

  /** No resource exists at the given id, or it is not visible to this caller. */
  const val NOT_FOUND: String = "not_found"

  /** The uploaded PGN does not parse, has no playable move, or plays an illegal move. */
  const val INVALID_PGN: String = "invalid_pgn"

  /** The request would exceed a per user quota. */
  const val QUOTA_EXCEEDED: String = "quota_exceeded"
}
