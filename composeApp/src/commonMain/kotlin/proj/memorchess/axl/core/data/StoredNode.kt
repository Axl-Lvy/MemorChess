package proj.memorchess.axl.core.data

import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

class StoredNode(
  override val positionKey: PositionKey,
  override val nextMoves: List<IStoredMove>,
  override val previousMoves: List<IStoredMove>,
) : IStoredNode {
  constructor(
    positionKey: PositionKey,
    previousMove: IStoredMove,
  ) : this(positionKey, emptyList(), listOf(previousMove))

  constructor(
    positionKey: PositionKey,
    linkedMoves: PreviousAndNextMoves,
  ) : this(
    positionKey,
    linkedMoves.previousMoves.sortedBy { it.move },
    linkedMoves.nextMoves.sortedBy { it.move },
  )
}
