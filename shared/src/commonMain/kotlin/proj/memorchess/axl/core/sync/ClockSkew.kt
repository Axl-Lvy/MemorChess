package proj.memorchess.axl.core.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * How far ahead of server time a row's [SyncRow.updatedAt] may be and still be accepted.
 *
 * Ordinary devices disagree with the server by seconds, so refusing on any skew at all would refuse
 * constantly. This tolerance accepts that noise verbatim while still catching a clock that is
 * wrong by hours or years.
 */
val SYNC_SKEW_TOLERANCE: Duration = 5.minutes

/**
 * Whether this row claims to have been written further into the future than [SYNC_SKEW_TOLERANCE]
 * allows, and must therefore be refused rather than stored.
 *
 * The server must never silently rewrite [SyncRow.updatedAt] to bring it into range. A rewritten
 * row diverges permanently from the copy the author still holds, because the author's own value is
 * later and so wins every subsequent comparison, which leaves that device disagreeing with every
 * other one forever. Refusing instead keeps the invariant that a stored row is byte identical to
 * the row that was sent.
 *
 * @param serverNow The server's clock at the moment the row is received.
 */
fun SyncRow.isTooFarAhead(serverNow: Instant): Boolean =
  updatedAt > serverNow + SYNC_SKEW_TOLERANCE
