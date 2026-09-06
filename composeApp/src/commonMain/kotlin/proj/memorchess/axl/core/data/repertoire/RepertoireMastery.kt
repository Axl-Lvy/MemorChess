package proj.memorchess.axl.core.data.repertoire

import proj.memorchess.axl.core.data.DataRepertoire
import proj.memorchess.axl.core.data.RepertoireMasterySnapshot
import proj.memorchess.axl.core.graph.TreeStore

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
 * The most recently trained repertoire's mastery snapshot, or `null` when no registered repertoire
 * has any trainable position yet. Ties in recency break on repertoire name, ascending.
 */
internal suspend fun mostRecentRepertoireMastery(treeStore: TreeStore): RepertoireMastery? {
  val repertoires = treeStore.repertoires()
  if (repertoires.isEmpty()) return null
  val snapshots = treeStore.repertoireMasterySnapshots()
  val (repertoire, snapshot) =
    repertoires
      .mapNotNull { repertoire -> snapshots[repertoire.id]?.let { repertoire to it } }
      .filter { (_, snapshot) -> snapshot.lastReview != null }
      .sortedWith(
        compareByDescending<Pair<DataRepertoire, RepertoireMasterySnapshot>> {
            it.second.lastReview
          }
          .thenBy { it.first.name }
      )
      .firstOrNull() ?: return null
  return RepertoireMastery(
    repertoireName = repertoire.name,
    solidCount = snapshot.solidCount,
    totalCount = snapshot.totalCount,
  )
}
