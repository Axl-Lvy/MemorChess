package proj.ankichess.axl.core.data

import proj.ankichess.axl.core.graph.nodes.PreviousAndNextMoves

class StoredNode(
  override val positionKey: proj.ankichess.axl.core.data.PositionKey,
  private val moveList: List<String>,
  private val previousMoveList: List<String>,
) : IStoredNode {
  constructor(
    positionKey: proj.ankichess.axl.core.data.PositionKey,
    previousMove: String,
  ) : this(positionKey, emptyList(), listOf(previousMove))

  constructor(
    positionKey: proj.ankichess.axl.core.data.PositionKey,
    linkedMoves: PreviousAndNextMoves,
  ) : this(positionKey, linkedMoves.previousMoves.sorted(), linkedMoves.nextMoves.sorted())

  override fun getAvailableMoveList(): List<String> {
    return moveList
  }

  override fun getPreviousMoveList(): List<String> {
    return previousMoveList
  }
}
