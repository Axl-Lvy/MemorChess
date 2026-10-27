package proj.memorchess.axl.core.scheduling

/**
 * Lifecycle phase of a card in the FSRS state machine. Decides whether the next interval is sub-day
 * (learning steps) or day grained (stability).
 */
enum class CardPhase {
  /** Brand new card that has never been reviewed. The next review starts the learning steps. */
  NEW,
  /** A new card working through the learning steps; due dates are sub-day until it graduates. */
  LEARNING,
  /** A graduated card on a day grained interval derived from its stability. */
  REVIEW,
  /** A lapsed review card working back through the relearning steps; due dates are sub-day. */
  RELEARNING,
}
