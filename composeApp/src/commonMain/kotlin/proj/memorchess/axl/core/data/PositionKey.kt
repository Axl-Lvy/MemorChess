package proj.memorchess.axl.core.data

import co.touchlab.kermit.Logger
import kotlin.jvm.JvmInline
import proj.memorchess.axl.core.engine.GameEngine

private val LOGGER = Logger.withTag("PositionKey")

/**
 * Represents a unique key for a chess position based on its cropped FEN representation. This key
 * can be used to create a [GameEngine] instance.
 *
 * @property value The cropped FEN string representing the chess position. It is not a full FEN as
 *   it does not include the move counts and not always the en passant column.
 */
@JvmInline
value class PositionKey(val value: String) {

  companion object {

    /** Position key for the standard starting position. */
    val START_POSITION = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")

    /**
     * Validates the given FEN string and creates a [PositionKey] if valid.
     *
     * @param fen The FEN string to validate and create the key from.
     * @return A [PositionKey] if the FEN is valid, or `null` if invalid.
     */
    fun validateAndCreateOrNull(fen: String): PositionKey? {
      val positionKey = PositionKey(fen)
      try {
        GameEngine(positionKey)
        return positionKey
      } catch (e: Exception) {
        LOGGER.e(e) { "Invalid FEN: $fen." }
        return null
      }
    }
  }
}
