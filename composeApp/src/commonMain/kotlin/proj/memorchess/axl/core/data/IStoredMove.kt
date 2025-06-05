package proj.memorchess.axl.core.data

interface IStoredMove {
  fun getOrigin(): PositionKey

  fun getDestination(): PositionKey

  val move: String

  val isGood: Boolean
}
