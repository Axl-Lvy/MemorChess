package proj.ankichess.axl.core.impl.data

import proj.ankichess.axl.core.intf.data.IStoredNode

class StoredNode(override val positionKey: PositionKey, private val moveList: List<String>) :
  IStoredNode {
  override fun getAvailableMoveList(): List<String> {
    return moveList
  }
}
