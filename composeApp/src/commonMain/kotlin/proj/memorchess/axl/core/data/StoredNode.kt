package proj.memorchess.axl.core.data

import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

class StoredNode(
  override val positionKey: PositionKey,
  override val nextMoves: List<StoredMove>,
  override val previousMoves: List<StoredMove>,
) : IStoredNode {

  constructor(
    positionKey: PositionKey,
    linkedMoves: PreviousAndNextMoves,
  ) : this(
    positionKey,
    linkedMoves.nextMoves.values.sortedBy { it.move },
    linkedMoves.previousMoves.values.sortedBy { it.move },
  )
}
