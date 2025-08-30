package proj.memorchess.axl.core.data

import com.diamondedge.logging.logging
import proj.memorchess.axl.core.engine.board.IPosition
import proj.memorchess.axl.core.engine.parser.FenParser

private val LOGGER = logging()

/**
 * Represents a unique key for a chess position based on its pseudo FEN representation. This key can
 * be used to create an [IPosition] instance.
 *
 * @property fenRepresentation The pseudo FEN string representing the chess position. It is not a
 *   full FEN as it does not include the move counts and not always the en passant column.
 */
data class PositionIdentifier(val fenRepresentation: String) {

  /**
   * Creates an [IPosition] from this key.
   *
   * @return An [IPosition] created from this key.
   */
  fun createPosition(): IPosition {
    return FenParser.readPosition(this)
  }

  companion object {

    /** Position identifier for the standard starting position. */
    val START_POSITION = PositionIdentifier("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")

    /**
     * Validates the given FEN string and creates a [PositionIdentifier] if valid.
     *
     * @param fen The FEN string to validate and create the identifier from.
     * @return A [PositionIdentifier] if the FEN is valid, or `null` if invalid.
     */
    fun validateAndCreateOrNull(fen: String): PositionIdentifier? {
      val positionIdentifier = PositionIdentifier(fen)
      try {
        positionIdentifier.createPosition()
        return positionIdentifier
      } catch (e: IllegalArgumentException) {
        LOGGER.warn { "Invalid FEN: $fen." }
        LOGGER.warn { e.stackTraceToString() }
        return null
      }
    }
  }
}
