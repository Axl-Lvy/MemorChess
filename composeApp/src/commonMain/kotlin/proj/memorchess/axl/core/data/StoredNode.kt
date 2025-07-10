package proj.memorchess.axl.core.data

import com.diamondedge.logging.logging
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

/**
 * Data class representing a node in the database.
 *
 * @property positionIdentifier The position.
 * @property previousAndNextMoves The linked moves.
 * @property previousAndNextTrainingDate The date when this node was last trained and when it should
 *   be trained next.
 * @constructor Creates a new node.
 */
data class StoredNode(
  val positionIdentifier: PositionIdentifier,
  val previousAndNextMoves: PreviousAndNextMoves,
  val previousAndNextTrainingDate: PreviousAndNextDate,
) {

  /** Saves this node. */
  suspend fun save() {
    LOGGER.info { "saving $this" }
    DatabaseHolder.getDatabase().insertPosition(this)
  }
}

private val LOGGER = logging()
