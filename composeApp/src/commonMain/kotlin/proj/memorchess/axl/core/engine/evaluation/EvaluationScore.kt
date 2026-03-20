package proj.memorchess.axl.core.engine.evaluation

/** Evaluation score from White's perspective. */
sealed interface EvaluationScore {
  /** Centipawn evaluation from White's perspective. */
  data class Centipawns(val value: Int) : EvaluationScore

  /** Forced mate. Positive means White mates, negative means Black mates. */
  data class Mate(val moves: Int) : EvaluationScore
}
