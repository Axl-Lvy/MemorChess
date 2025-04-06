package proj.ankichess.axl.core.impl.engine.pieces

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.intf.engine.pieces.IPiece

abstract class APiece(override val player: Game.Player) : IPiece {

  abstract fun baseChar(): String

  override fun toString(): String {
    val char = baseChar()
    return if (player == Game.Player.WHITE) char.uppercase() else char
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is APiece) return false

    if (player != other.player) return false
    if (baseChar() != other.baseChar()) return false

    return true
  }

  override fun hashCode(): Int {
    var result = player.hashCode()
    result = 31 * result + baseChar().hashCode()
    return result
  }
}
