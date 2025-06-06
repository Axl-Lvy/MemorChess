package proj.memorchess.axl.core.data

/** Move that can be stored in [ICommonDatabase] */
interface IStoredMove {

  /** Origin position of the move */
  fun getOrigin(): PositionKey

  /** Destination position of the move */
  fun getDestination(): PositionKey

  /** The move in standard notation */
  val move: String

  /** Whether the move has to be learned */
  val isGood: Boolean
}
