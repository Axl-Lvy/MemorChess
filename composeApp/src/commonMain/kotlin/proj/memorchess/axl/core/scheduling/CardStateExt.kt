package proj.memorchess.axl.core.scheduling

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

/**
 * Convenience factories for [CardState].
 *
 * These are deliberately not member functions of [CardState] because they reach into [DateUtil] and
 * therefore tie a pure value type to a clock. Keeping them as top level extensions makes unit
 * testing easier and signals the dependency at the call site.
 */
object CardStateFactory {

  /**
   * Builds a brand new [CardState] anchored on [now]. Stability and difficulty are zero; the card
   * is due immediately. This is the canonical replacement for the previous
   * `PreviousAndNextDate.dummyToday()` factory.
   */
  fun new(now: Instant = DateUtil.now()): CardState =
    CardState(
      dueDate = now,
      lastReview = null,
      stability = 0.0,
      difficulty = 0.0,
      reps = 0,
      lapses = 0,
    )
}
