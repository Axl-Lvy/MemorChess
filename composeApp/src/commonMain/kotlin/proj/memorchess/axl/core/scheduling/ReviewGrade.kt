package proj.memorchess.axl.core.scheduling

/** User assessment of a single review; [value] is the canonical FSRS rating. */
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
