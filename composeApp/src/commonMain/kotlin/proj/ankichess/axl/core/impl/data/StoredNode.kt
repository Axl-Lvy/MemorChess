package proj.ankichess.axl.core.impl.data

import proj.ankichess.axl.core.intf.data.IStoredNode

class StoredNode(override val positionKey: PositionKey, private val moveList: List<String>) :
  IStoredNode {
  constructor(positionKey: PositionKey) : this(positionKey, emptyList())

  override fun getAvailableMoveList(): List<String> {
    return moveList
  }
}
