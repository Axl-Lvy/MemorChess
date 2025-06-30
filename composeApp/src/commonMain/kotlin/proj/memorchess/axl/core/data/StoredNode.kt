package proj.memorchess.axl.core.data

import com.diamondedge.logging.logging
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

data class StoredNode(
  override val positionKey: PositionKey,
  override val previousAndNextMoves: PreviousAndNextMoves,
  override val previousAndNextTrainingDate: PreviousAndNextDate,
) : IStoredNode {

  suspend fun save() {
    LOGGER.info { "saving $this" }
    DatabaseHolder.getDatabase().insertPosition(this)
  }
}

private val LOGGER = logging()
