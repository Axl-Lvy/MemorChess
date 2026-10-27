package proj.memorchess.axl.core.sync

import kotlinx.serialization.Serializable

/**
 * The body of every response the server does not answer with a success payload.
 *
 * @property code One of [ApiErrorCode]; clients branch on it, never on [message].
 * @property message Human readable explanation, safe to log and to show.
 */
@Serializable data class ApiError(val code: String, val message: String)

/**
 * Causes a request can fail for. Plain strings rather than an enum, so an older client survives a
 * code it has never heard of.
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

  /** The resource existed but was permanently removed and can never be republished. */
  const val REMOVED: String = "removed"

  /** No resource exists at the given id, or it is not visible to this caller. */
  const val NOT_FOUND: String = "not_found"

  /** The uploaded repertoire has no move, an illegal move, or a move not connected to the start. */
  const val INVALID_REPERTOIRE: String = "invalid_repertoire"

  /** The request would exceed a per user quota. */
  const val QUOTA_EXCEEDED: String = "quota_exceeded"

  /** The caller (or its IP, when unauthenticated) exceeded its request budget. */
  const val RATE_LIMITED: String = "rate_limited"

  /**
   * The calling device was removed and has fallen below what garbage collection already purged. It
   * must wipe its synced local state and register again reporting the wipe.
   */
  const val RESYNC_REQUIRED: String = "resync_required"
}
