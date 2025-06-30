package proj.memorchess.axl.core.data

import proj.memorchess.axl.core.engine.board.IPosition
import proj.memorchess.axl.core.engine.parser.FenParser

/**
 * Represents a unique key for a chess position based on its pseudo FEN representation. This key can
 * be used to create an [IPosition] instance.
 *
 * @property fenRepresentation The pseudo FEN string representing the chess position. It is not a
 *   full FEN as it does not include the move counts and not always the en passant column.
 */
data class PositionKey(val fenRepresentation: String) {

  /**
   * Creates an [IPosition] from this key.
   *
   * @return An [IPosition] created from this key.
   */
  fun createPosition(): IPosition {
    return FenParser.readPosition(this)
  }

  companion object {
    val START_POSITION = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  }
}
