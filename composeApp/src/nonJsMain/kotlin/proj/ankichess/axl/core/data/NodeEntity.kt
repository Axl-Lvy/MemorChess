package proj.ankichess.axl.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an [IStoredNode] ready to be stored in the database.
 *
 * @property fenRepresentation FEN string uniquely identifying the chess position.
 * @property availableMoves Comma-separated list of moves from this position.
 */
@Entity(tableName = "NodeEntity")
data class NodeEntity(

  /**
   * FEN string uniquely identifying the chess position. Used as the primary key.
   *
   * Note that it should not be always the exact FEN string: it does not keep useless en passant
   * information.
   */
  @PrimaryKey(autoGenerate = false) val fenRepresentation: String,

  /** Comma-separated list of moves from this position. */
  val availableMoves: String,

  /** Comma-separated list of moves that can lead to this position. */
  val previousMoves: String,
) : IStoredNode {

  /** Returns the [PositionKey] for this node, constructed from the FEN representation. */
  override val positionKey: proj.ankichess.axl.core.data.PositionKey
    get() = proj.ankichess.axl.core.data.PositionKey(fenRepresentation)

  /** Returns the list of available moves by splitting the comma-separated string. */
  override fun getAvailableMoveList(): List<String> =
    availableMoves.split(",").filter { it.isNotBlank() }

  /** Returns the list of previous moves by splitting the comma-separated string. */
  override fun getPreviousMoveList(): List<String> =
    previousMoves.split(",").filter { it.isNotBlank() }

  /** Converter object for creating [NodeEntity] instances from [IStoredNode]s. */
  companion object Converter {
    /**
     * Converts an [IStoredNode] to a [NodeEntity].
     *
     * @param position The [IStoredNode] to convert.
     * @return A [NodeEntity] with the same FEN and available moves.
     */
    fun convertToEntity(position: IStoredNode): NodeEntity {
      return NodeEntity(
        fenRepresentation = position.positionKey.fenRepresentation,
        availableMoves = position.getAvailableMoveList().joinToString(","),
        previousMoves = position.getPreviousMoveList().joinToString(","),
      )
    }
  }
}
