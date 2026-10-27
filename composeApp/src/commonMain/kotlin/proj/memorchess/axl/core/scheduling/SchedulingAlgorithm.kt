package proj.memorchess.axl.core.scheduling

import kotlin.time.Instant

/** Pluggable spaced repetition scheduler. */
interface SchedulingAlgorithm {

  /** Builds the initial state of a brand new card, due at [now]. */
  fun initial(now: Instant): CardState

  /**
   * Computes the next [CardState] after a review graded [grade] at [now]. A `null` [previous]
   * starts from [initial].
   */
  fun schedule(previous: CardState?, grade: ReviewGrade, now: Instant): CardState
}
