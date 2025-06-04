package proj.memorchess.axl.core.data

interface IStoredNode {
  val positionKey: proj.memorchess.axl.core.data.PositionKey

  fun getAvailableMoveList(): List<String>

  fun getPreviousMoveList(): List<String>
}
