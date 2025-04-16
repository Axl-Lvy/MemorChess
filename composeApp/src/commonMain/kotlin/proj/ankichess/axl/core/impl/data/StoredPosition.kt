package proj.ankichess.axl.core.impl.data

import proj.ankichess.axl.core.intf.data.IStoredPosition

class StoredPosition(override val fenRepresentation: String, private val moveList: List<String>) :
  IStoredPosition {
  override fun getAvailableMoveList(): List<String> {
    return moveList
  }
}
