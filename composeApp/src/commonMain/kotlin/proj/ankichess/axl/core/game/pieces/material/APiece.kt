package proj.ankichess.axl.core.game.pieces.material

import proj.ankichess.axl.core.game.Game

abstract class APiece(override val player: Game.Player) : IPiece {

  abstract fun baseChar(): String

  override fun toString(): String {
    val char = baseChar()
    return if (player == Game.Player.WHITE) char.uppercase() else char
  }
}
