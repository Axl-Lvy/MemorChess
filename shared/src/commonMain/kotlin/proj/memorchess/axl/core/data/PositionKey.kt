package proj.memorchess.axl.core.data

import co.touchlab.kermit.Logger
import kotlin.jvm.JvmInline
import proj.memorchess.axl.core.engine.GameEngine

private val LOGGER = Logger.withTag("PositionKey")

/**
 * Unique key of a chess position: its cropped FEN, without the move counts and not always with the
 * en passant column.
 */
@JvmInline
value class PositionKey(val value: String) {

  companion object {

    /** Position key for the standard starting position. */
    val START_POSITION = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")

    /** Returns the [PositionKey] of [fen], or `null` when it does not load into a [GameEngine]. */
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
