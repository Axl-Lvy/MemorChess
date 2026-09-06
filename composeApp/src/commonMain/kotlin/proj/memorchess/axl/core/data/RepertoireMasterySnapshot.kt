package proj.memorchess.axl.core.data

import kotlin.time.Instant

/**
 * One repertoire's mastery aggregate over its trainable positions.
 *
 * @property solidCount Trainable positions whose card has graduated to
 *   [proj.memorchess.axl.core.scheduling.CardPhase.REVIEW].
 * @property totalCount Every trainable position tagged with this repertoire.
 * @property lastReview Latest review moment among this repertoire's trainable positions, or `null`
 *   when none has ever been reviewed.
 */
data class RepertoireMasterySnapshot(
  val solidCount: Int,
  val totalCount: Int,
  val lastReview: Instant?,
)
