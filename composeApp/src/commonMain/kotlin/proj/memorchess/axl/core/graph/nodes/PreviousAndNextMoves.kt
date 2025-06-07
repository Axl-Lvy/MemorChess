package proj.memorchess.axl.core.graph.nodes

import proj.memorchess.axl.core.data.StoredMove

data class PreviousAndNextMoves(
  val previousMoves: MutableMap<String, StoredMove>,
  val nextMoves: MutableMap<String, StoredMove>,
) {
  constructor() : this(mutableMapOf(), mutableMapOf())

  constructor(
    previousMoves: Collection<StoredMove>,
    nextMoves: Collection<StoredMove>,
  ) : this(
    previousMoves.associateBy { it.move }.toMutableMap(),
    nextMoves.associateBy { it.move }.toMutableMap(),
  )

  fun addPreviousMove(move: StoredMove): StoredMove? {
    return previousMoves.put(move.move, move)
  }

  fun addNextMove(move: StoredMove): StoredMove? {
    return nextMoves.put(move.move, move)
  }

  fun setPreviousMovesAsGood(isGood: Boolean) {
    previousMoves.values.forEach { it.isGood = isGood }
  }
}
