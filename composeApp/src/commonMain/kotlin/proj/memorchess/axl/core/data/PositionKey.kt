package proj.memorchess.axl.core.data

import proj.memorchess.axl.core.engine.board.IPosition
import proj.memorchess.axl.core.engine.parser.FenParser

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
