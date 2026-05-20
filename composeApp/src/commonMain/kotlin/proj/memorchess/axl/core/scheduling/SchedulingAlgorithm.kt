package proj.memorchess.axl.core.scheduling

import kotlin.time.Instant

/**
 * Pluggable spaced repetition scheduler.
 *
 * Implementations consume an optional [CardState] plus a [ReviewGrade] and return the next state
 * for the card. The interface is intentionally narrow so that call sites stay algorithm agnostic
 * and so that swapping a concrete algorithm requires no caller changes.
 */
interface SchedulingAlgorithm {

  /**
   * Builds the initial state of a brand new card.
   *
   * @param now Current moment. Used as the initial due date.
   */
  fun initial(now: Instant): CardState

  /**
   * Computes the next [CardState] after a review.
   *
   * @param previous Existing state of the card, or `null` to treat the review as a first review.
   *   When `null`, the implementation is expected to start from [initial] and then apply [grade].
   * @param grade User assessment of the review.
   * @param now Moment at which the review took place.
   */
  fun schedule(previous: CardState?, grade: ReviewGrade, now: Instant): CardState
}
