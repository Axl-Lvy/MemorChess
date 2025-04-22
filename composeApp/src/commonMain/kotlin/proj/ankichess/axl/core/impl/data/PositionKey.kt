package proj.ankichess.axl.core.impl.data

import proj.ankichess.axl.core.impl.engine.parser.FenParser
import proj.ankichess.axl.core.intf.engine.board.IPosition

class PositionKey(val fenRepresentation: String) {
  fun createPosition(): IPosition {
    return FenParser.readPosition(this)
  }

  override fun toString(): String {
    return "PositionKey(fenRepresentation='$fenRepresentation')"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PositionKey) return false
    return fenRepresentation == other.fenRepresentation
  }

  override fun hashCode(): Int {
    return fenRepresentation.hashCode()
  }
}
