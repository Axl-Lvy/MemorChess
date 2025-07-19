package proj.memorchess.axl.core.data

import com.diamondedge.logging.logging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
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
) : KoinComponent {

  private val db by inject<DatabaseQueryManager>()

  /** Saves this node. */
  suspend fun save(remoteDatabaseManager: DatabaseSynchronizer?) {
    LOGGER.info { "saving $this" }
    remoteDatabaseManager?.saveStoredNode(this)
    db.insertPosition(this)
  }
}

private val LOGGER = logging()
