package proj.memorchess.axl.core.graph.nodes

import proj.memorchess.axl.core.data.IStoredMove

class PreviousAndNextMoves(
  val previousMoves: MutableSet<IStoredMove>,
  val nextMoves: MutableSet<IStoredMove>,
) {
  constructor() : this(mutableSetOf(), mutableSetOf())

  constructor(
    previousMoves: Collection<IStoredMove>,
    nextMoves: Collection<IStoredMove>,
  ) : this(previousMoves.toMutableSet(), nextMoves.toMutableSet())

  constructor(previousMove: IStoredMove) : this(mutableSetOf(previousMove), mutableSetOf())

  fun addPreviousMove(move: IStoredMove) {
    previousMoves.add(move)
  }

  fun addNextMove(move: IStoredMove) {
    nextMoves.add(move)
  }

  fun setPreviousMovesAsGood(isGood: Boolean) {
    previousMoves.forEach { it.isGood = isGood }
  }
}
