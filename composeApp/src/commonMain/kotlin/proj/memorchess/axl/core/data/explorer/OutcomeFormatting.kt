package proj.memorchess.axl.core.data.explorer

private const val THOUSAND = 1_000L
private const val MILLION = 1_000_000L
private const val BILLION = 1_000_000_000L

/** Formats a game count compactly with at most one decimal, truncating (`1_299` → `"1.2k"`). */
internal fun Long.toCompactCount(): String =
  when {
    this >= BILLION -> compact(BILLION, "B")
    this >= MILLION -> compact(MILLION, "M")
    this >= THOUSAND -> compact(THOUSAND, "k")
    else -> toString()
  }

private fun Long.compact(unit: Long, suffix: String): String {
  val tenths = this / (unit / 10)
  val decimal = tenths % 10
  return if (decimal == 0L) "${tenths / 10}$suffix" else "${tenths / 10}.$decimal$suffix"
}

/** Whole-number shares of white wins, draws and black wins that always sum to 100. */
internal data class OutcomePercents(val white: Int, val draws: Int, val black: Int) {
  companion object {
    /** Splits the counts by largest remainder, or returns `null` when there are no games. */
    fun of(white: Long, draws: Long, black: Long): OutcomePercents? {
      // Summed as Double: a Long sum of three API counts can overflow.
      val total = white.toDouble() + draws.toDouble() + black.toDouble()
      if (total == 0.0) return null
      val exact = listOf(white, draws, black).map { 100.0 * it / total }
      val floors = exact.map { it.toInt() }.toMutableList()
      val leftover = 100 - floors.sum()
      exact.indices
        .sortedByDescending { exact[it] - floors[it] }
        .take(leftover)
        .forEach { floors[it]++ }
      return OutcomePercents(floors[0], floors[1], floors[2])
    }
  }
}

/** Labels a share as its [percent], or `"<1"` when games exist but round down to zero. */
internal fun shareLabel(count: Long, percent: Int): String =
  if (count > 0 && percent == 0) "<1" else percent.toString()
