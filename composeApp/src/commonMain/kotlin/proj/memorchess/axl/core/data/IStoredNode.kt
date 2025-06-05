package proj.memorchess.axl.core.data

interface IStoredNode {
  val positionKey: PositionKey

  val nextMoves: List<IStoredMove>

  val previousMoves: List<IStoredMove>
}
