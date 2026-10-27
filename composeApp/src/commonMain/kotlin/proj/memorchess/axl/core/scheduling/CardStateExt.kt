package proj.memorchess.axl.core.scheduling

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

/** Factories for [CardState], kept off the value type so it stays independent of the clock. */
object CardStateFactory {

  /** A brand new [CardState] due at [now], with zero stability and difficulty. */
  fun new(now: Instant = DateUtil.now()): CardState =
    CardState(
      dueDate = now,
      lastReview = null,
      firstReview = null,
      stability = 0.0,
      difficulty = 0.0,
      reps = 0,
      lapses = 0,
    )
}
