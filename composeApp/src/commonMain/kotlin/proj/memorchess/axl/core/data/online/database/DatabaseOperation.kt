package proj.memorchess.axl.core.data.online.database

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.PositionIdentifier

/** Sealed class representing database operations that can be queued for upload. */
sealed class DatabaseOperation {

  /**
   * Operation to insert moves and their associated positions into the database.
   *
   * @property moves The moves to insert.
   * @property positions The position metadata associated with the moves.
   */
  data class InsertMoves(val moves: List<DataMove>, val positions: List<DataPosition>) : DatabaseOperation()

  /**
   * Operation to delete a position from the database.
   *
   * @property position The position identifier to delete.
   */
  data class DeletePosition(val position: PositionIdentifier, val updatedAt: Instant) : DatabaseOperation()

  /**
   * Operation to delete a move from the database.
   *
   * @property origin The origin position of the move.
   * @property move The move notation to delete.
   */
  data class DeleteMove(val origin: PositionIdentifier, val move: String, val updatedAt: Instant) : DatabaseOperation()

  /**
   * Operation to delete all positions and moves.
   *
   * @property hardFrom Entities updated after this date will be hard-deleted.
   */
  data class DeleteAll(val hardFrom: Instant?) : DatabaseOperation()
}
