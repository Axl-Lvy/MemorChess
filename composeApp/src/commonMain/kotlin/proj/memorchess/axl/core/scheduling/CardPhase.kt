package proj.memorchess.axl.core.scheduling

/**
 * Lifecycle phase of a card in the FSRS state machine.
 *
 * Mirrors the four canonical FSRS states. The phase decides whether the next interval is sub-day
 * (driven by learning steps) or day grained (driven by stability), and is what
 * [proj.memorchess.axl.core.graph.TrainingScheduler] reads to decide if a card stays in the current
 * session or leaves it.
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
