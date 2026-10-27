package proj.memorchess.axl.core.data

import kotlin.time.Instant

/**
 * One repertoire's mastery aggregate over its trainable positions.
 *
 * @property solidCount Trainable positions graduated to
 *   [proj.memorchess.axl.core.scheduling.CardPhase.REVIEW].
 * @property lastReview Latest review among the trainable positions, or `null` if none was reviewed.
 */
data class RepertoireMasterySnapshot(
  val solidCount: Int,
  val totalCount: Int,
  val lastReview: Instant?,
)
