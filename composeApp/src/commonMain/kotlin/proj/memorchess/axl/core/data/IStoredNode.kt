package proj.memorchess.axl.core.data

/** Node that can be stored in [ICommonDatabase] */
interface IStoredNode {

  /** The key of the position, used to uniquely identify it */
  val positionKey: PositionKey

  /** The list of next moves from this position */
  val nextMoves: MutableList<StoredMove>

  /** The list of previous moves leading to this position */
  val previousMoves: MutableList<StoredMove>
}
