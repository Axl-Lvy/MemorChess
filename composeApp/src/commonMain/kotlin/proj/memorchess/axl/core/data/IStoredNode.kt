package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDate

/** Node that can be stored in [ICommonDatabase] */
interface IStoredNode {

  /** The key of the position, used to uniquely identify it */
  val positionKey: PositionKey

  /** The list of next moves from this position */
  val nextMoves: MutableList<StoredMove>

  /** The list of previous moves leading to this position */
  val previousMoves: MutableList<StoredMove>

  /** The date when this node was last trained */
  val lastTrainedDate: LocalDate

  /** The date when this node should be trained next */
  val nextTrainedDate: LocalDate
}
