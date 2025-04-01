package proj.ankichess.axl.core.impl.engine.pieces

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.intf.engine.pieces.IPiece

abstract class APiece(override val player: Game.Player) : IPiece {

  abstract fun baseChar(): String

  override fun toString(): String {
    val char = baseChar()
    return if (player == Game.Player.WHITE) char.uppercase() else char
  }
}
