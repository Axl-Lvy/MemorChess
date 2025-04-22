package proj.ankichess.axl.core.intf.data

import proj.ankichess.axl.core.impl.data.PositionKey

interface IStoredNode {
  val positionKey: PositionKey

  fun getAvailableMoveList(): List<String>
}
