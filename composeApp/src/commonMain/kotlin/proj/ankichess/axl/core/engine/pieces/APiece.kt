package proj.ankichess.axl.core.engine.pieces

import proj.ankichess.axl.core.engine.Game

abstract class APiece(override val player: Game.Player) : IPiece {

  abstract fun baseChar(): String

  override fun toString(): String {
    val char = baseChar()
    return if (player == Game.Player.WHITE) char.uppercase() else char
  }
}
