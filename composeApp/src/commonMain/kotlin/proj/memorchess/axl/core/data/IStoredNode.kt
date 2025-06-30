package proj.memorchess.axl.core.data

import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

/** Node that can be stored in [ICommonDatabase] */
interface IStoredNode {

  /** The key of the position, used to uniquely identify it */
  val positionKey: PositionKey

  /** The list of previous moves leading to this position and next moves from this position */
  val previousAndNextMoves: PreviousAndNextMoves

  /** The date when this node was last trained and when it should be trained next */
  val previousAndNextTrainingDate: PreviousAndNextDate
}
