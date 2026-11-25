package proj.memorchess.axl.dev

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DailyActivityStore
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImporter
import proj.memorchess.axl.core.pgn.PgnParser

private const val NODES_PAGE_SIZE = 500

/**
 * Fills an empty database with [SEED_REPERTOIRES] and a simulated review history, through the same
 * stores the app writes with, so the seed queues itself for sync like any local edit.
 */
internal class DevSeeder(
  private val tagStore: RepertoireTagStore,
  private val importer: PgnImporter,
  private val treeStore: TreeStore,
  private val database: DatabaseQueryManager,
  private val activityStore: DailyActivityStore,
  private val simulator: SeedSimulator,
) {

  /** Seeds the repertoires and a review history ending the day before [now]. */
  suspend fun seed(now: Instant) {
    for (repertoire in SEED_REPERTOIRES) {
      tagStore.register(repertoire.id, repertoire.name, repertoire.color)
      importer.import(PgnParser.parse(repertoire.pgn), repertoire.perspective, repertoire.id)
    }
    val result = simulator.simulate(cardsInIntroductionOrder(), now)
    for ((positionKey, state) in result.cardStates) treeStore.updateCardState(positionKey, state)
    for (record in result.activity) activityStore.putRecord(record)
  }

  private suspend fun cardsInIntroductionOrder() =
    allNodes()
      .filter { it.hasGoodOutgoing }
      .sortedWith(compareBy({ it.depth }, { it.positionKey.value }))
      .map { it.positionKey }

  private suspend fun allNodes(): List<DataNode> {
    val nodes = mutableListOf<DataNode>()
    var cursor: String? = null
    do {
      val page = database.getNodesPage(cursor, NODES_PAGE_SIZE)
      nodes += page.nodes
      cursor = page.nextCursor
    } while (cursor != null)
    return nodes
  }
}
