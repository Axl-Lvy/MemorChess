package proj.memorchess.axl.core.graph.nodes

class PreviousAndNextMoves(
  val previousMoves: MutableSet<String>,
  val nextMoves: MutableSet<String>,
) {
  constructor() : this(mutableSetOf(), mutableSetOf())

  constructor(
    previousMoves: Collection<String>,
    nextMoves: Collection<String>,
  ) : this(previousMoves.toMutableSet(), nextMoves.toMutableSet())

  constructor(previousMove: String) : this(mutableSetOf(previousMove), mutableSetOf())

  fun addPreviousMove(move: String) {
    previousMoves.add(move)
  }

  fun addNextMove(move: String) {
    nextMoves.add(move)
  }
}
