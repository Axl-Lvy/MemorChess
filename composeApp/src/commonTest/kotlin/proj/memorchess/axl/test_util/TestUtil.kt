package proj.memorchess.axl.test_util

import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager

/** Default page size used when draining the whole store one bounded page at a time in tests. */
private const val DRAIN_PAGE_SIZE = 256

/**
 * Drains every non deleted node out of [database] by looping the bounded [getNodesPage] read until
 * the cursor terminates, returning them as a single list.
 *
 * This is test only assembly of a bounded fixture: production code never collects the whole store.
 * It exists so tests that used to call the removed `getAllNodes` can keep asserting over the full
 * live set while still exercising the paged read path the app uses.
 *
 * @param database The store to read from.
 * @return Every live node, in position key ascending order.
 */
suspend fun drainAllNodes(database: DatabaseQueryManager): List<DataNode> {
  val nodes = mutableListOf<DataNode>()
  var cursor: String? = null
  do {
    val page = database.getNodesPage(cursor, DRAIN_PAGE_SIZE)
    nodes.addAll(page.nodes)
    cursor = page.nextCursor
  } while (cursor != null)
  return nodes
}

/**
 * Get button description according to string resource.
 *
 * @param buttonName Name of the button to get description for.
 */
fun getNavigationButtonDescription(buttonName: String): String {
  return "$buttonName button"
}

/**
 * Get tile description according to string resource.
 *
 * @param tileName Name of the tile to get description for.
 */
fun getTileDescription(tileName: String): String {
  return "Tile $tileName"
}

/**
 * Get next move button description according to string resource.
 *
 * @param move Name of the button to get description for.
 */
fun getNextMoveDescription(move: String): String {
  return "Play $move"
}

/**
 * Get a piece description according to string resource.
 *
 * @param piece Name of the piece to get description for.
 */
fun getPieceDescription(piece: String): String {
  return "Piece $piece"
}

val TEST_TIMEOUT = 10.seconds

/** Safety ceiling for an asynchronous board load to settle; generous so slow hosts don't flake. */
val BOARD_LOAD_TIMEOUT = 30.seconds
