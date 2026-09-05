package proj.memorchess.axl.core.data.repertoire

/**
 * A repertoire's "N of M positions solid" mastery snapshot, shaped the way the Today page's
 * pick-up-where-you-left-off card wants to show it.
 */
internal data class RepertoireMastery(
  val repertoireName: String,
  val solidCount: Int,
  val totalCount: Int,
) {

  /** Percentage of solid positions rounded to the nearest whole number, 0 when there are none. */
  val solidPercent: Int
    get() = if (totalCount == 0) 0 else (solidCount * 100 + totalCount / 2) / totalCount
}

/**
 * Placeholder mastery snapshot for the Today page, standing in until the real per-repertoire
 * aggregation (#292) tags positions by the repertoire they came from. Same precedent as
 * `ExploreStatBadgesRow`'s hardcoded stats.
 */
internal fun placeholderRepertoireMastery(): RepertoireMastery =
  RepertoireMastery(repertoireName = "Italian Game", solidCount = 46, totalCount = 68)
