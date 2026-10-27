package proj.memorchess.axl.test_util

import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager

/** Default page size used when draining the whole store one bounded page at a time in tests. */
private const val DRAIN_PAGE_SIZE = 256

/** Reads every non deleted node of [database] page by page, in position key ascending order. */
internal suspend fun drainAllNodes(database: DatabaseQueryManager): List<DataNode> {
  val nodes = mutableListOf<DataNode>()
  var cursor: String? = null
  do {
    val page = database.getNodesPage(cursor, DRAIN_PAGE_SIZE)
    nodes.addAll(page.nodes)
    cursor = page.nextCursor
  } while (cursor != null)
  return nodes
}

/** Returns the content description of the navigation button named [buttonName]. */
fun getNavigationButtonDescription(buttonName: String): String {
  return "$buttonName button"
}

/** Returns the content description of the tile named [tileName]. */
fun getTileDescription(tileName: String): String {
  return "Tile $tileName"
}

/** Returns the content description of the button playing [move]. */
fun getNextMoveDescription(move: String): String {
  return "Play $move"
}

/** Returns the content description of [piece]. */
fun getPieceDescription(piece: String): String {
  return "Piece $piece"
}

val TEST_TIMEOUT = 10.seconds

/** Safety ceiling for an asynchronous board load to settle; generous so slow hosts don't flake. */
val BOARD_LOAD_TIMEOUT = 30.seconds
