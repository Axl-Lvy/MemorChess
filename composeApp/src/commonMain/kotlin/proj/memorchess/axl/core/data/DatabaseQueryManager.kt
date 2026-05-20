package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.graph.DeleteMode

/**
 * Low level persistence seam for the opening tree.
 *
 * Only [proj.memorchess.axl.core.graph.TreeStore] and the platform specific implementations are
 * expected to touch this interface. The rest of the application talks to
 * [proj.memorchess.axl.core.graph.TreeStore].
 */
interface DatabaseQueryManager {

  /**
   * Retrieves all stored positions.
   *
   * @param withDeletedOnes When `true`, includes rows flagged with [DataNode.isDeleted].
   */
  suspend fun getAllNodes(withDeletedOnes: Boolean = false): List<DataNode>

  /** Retrieves a specific position, or `null` when missing or soft deleted. */
  suspend fun getPosition(positionKey: PositionKey): DataNode?

  /**
   * Deletes a single position and any incident moves.
   *
   * @param position Position to remove.
   * @param mode See [DeleteMode]. [DeleteMode.HARD] physically removes the row.
   */
  suspend fun deletePosition(position: PositionKey, mode: DeleteMode = DeleteMode.HARD)

  /**
   * Deletes a single move.
   *
   * @param origin Origin of the move.
   * @param move Move in standard algebraic notation.
   * @param mode See [DeleteMode]. [DeleteMode.HARD] physically removes the row.
   */
  suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode = DeleteMode.HARD)

  /** Hard wipe of every node and move. */
  suspend fun eraseAll()

  /**
   * Inserts new positions.
   *
   * @param positions The [DataNode] objects to insert.
   */
  suspend fun insertNodes(vararg positions: DataNode)

  /** Retrieves the latest `updatedAt` across nodes and moves. */
  suspend fun getLastUpdate(): Instant?
}
