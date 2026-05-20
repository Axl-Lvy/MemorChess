package proj.memorchess.axl.core.scheduling

/**
 * User assessment of a single review.
 *
 * Order matches the canonical FSRS rating values: 1 = AGAIN (lapse), 2 = HARD, 3 = GOOD, 4 = EASY.
 *
 * Other spaced repetition algorithms expose the same four buckets, so this enum is intended as a
 * stable cross algorithm vocabulary.
 */
enum class ReviewGrade(val value: Int) {
  /** Card was forgotten and must be relearned. */
  AGAIN(1),
  /** Card was recalled with difficulty. */
  HARD(2),
  /** Card was recalled correctly. */
  GOOD(3),
  /** Card was recalled effortlessly. */
  EASY(4),
}
