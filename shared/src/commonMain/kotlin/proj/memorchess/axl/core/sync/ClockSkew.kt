package proj.memorchess.axl.core.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * How far ahead of server time a row's [SyncRow.updatedAt] may be and still be accepted: enough to
 * absorb ordinary clock noise, not a clock that is wrong by hours.
 */
val SYNC_SKEW_TOLERANCE: Duration = 5.minutes

/**
 * Whether this row is further ahead of [serverNow] than [SYNC_SKEW_TOLERANCE] allows. Such a row is
 * refused, never rewritten: a rewritten row diverges from its author's copy forever.
 */
fun SyncRow.isTooFarAhead(serverNow: Instant): Boolean = updatedAt > serverNow + SYNC_SKEW_TOLERANCE
