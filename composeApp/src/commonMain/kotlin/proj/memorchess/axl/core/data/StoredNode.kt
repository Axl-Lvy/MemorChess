package proj.memorchess.axl.core.data

import com.diamondedge.logging.logging
import kotlinx.datetime.LocalDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.ui.util.DateUtil

data class StoredNode(
  override val positionKey: PositionKey,
  override val previousMoves: MutableList<StoredMove>,
  override val nextMoves: MutableList<StoredMove>,
  override val lastTrainedDate: LocalDate = DateUtil.today(),
  override val nextTrainedDate: LocalDate = DateUtil.today(),
) : IStoredNode {

  constructor(
    positionKey: PositionKey,
    linkedMoves: PreviousAndNextMoves,
    lastTrainedDate: LocalDate = DateUtil.today(),
    nextTrainedDate: LocalDate = DateUtil.today(),
  ) : this(
    positionKey,
    linkedMoves.previousMoves.values.sortedBy { it.move }.toMutableList(),
    linkedMoves.nextMoves.values.sortedBy { it.move }.toMutableList(),
    lastTrainedDate,
    nextTrainedDate,
  )

  suspend fun save() {
    LOGGER.info { "saving $this" }
    DatabaseHolder.getDatabase().insertPosition(this)
  }
}

private val LOGGER = logging()
