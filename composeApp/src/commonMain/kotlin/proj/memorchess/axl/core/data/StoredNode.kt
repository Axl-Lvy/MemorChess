package proj.memorchess.axl.core.data

import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

class StoredNode(
  override val positionKey: PositionKey,
  override val previousMoves: MutableList<StoredMove>,
  override val nextMoves: MutableList<StoredMove>,
) : IStoredNode {

  constructor(
    positionKey: PositionKey,
    linkedMoves: PreviousAndNextMoves,
  ) : this(
    positionKey,
    linkedMoves.previousMoves.values.sortedBy { it.move }.toMutableList(),
    linkedMoves.nextMoves.values.sortedBy { it.move }.toMutableList(),
  )
}
