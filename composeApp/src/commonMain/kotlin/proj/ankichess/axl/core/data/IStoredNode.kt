package proj.ankichess.axl.core.data

interface IStoredNode {
  val positionKey: proj.ankichess.axl.core.data.PositionKey

  fun getAvailableMoveList(): List<String>

  fun getPreviousMoveList(): List<String>
}
