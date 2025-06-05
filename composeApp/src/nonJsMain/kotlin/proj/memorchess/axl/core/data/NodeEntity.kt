package proj.memorchess.axl.core.data

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
  @PrimaryKey(autoGenerate = false) val fenRepresentation: String
)
