package proj.memorchess.axl.core.scheduling

import kotlin.jvm.JvmInline
import kotlin.math.roundToInt

/** Probability, between zero and one, that the user still recalls a card. */
@JvmInline
value class Retrievability(val value: Double) {
  init {
    require(value in 0.0..1.0) { "Retrievability must be within [0, 1], got $value" }
  }

  /** This probability as a whole percentage, rounded to the nearest integer. */
  fun toPercent(): Int = (value * 100).roundToInt()
}
