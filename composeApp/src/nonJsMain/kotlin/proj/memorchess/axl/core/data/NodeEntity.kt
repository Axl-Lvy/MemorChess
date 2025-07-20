package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate

/**
 * Entity representing an [StoredNode] ready to be stored in the database.
 *
 * @property fenRepresentation FEN string uniquely identifying the chess position.
 * @property lastTrainedDate The date when this node was last trained.
 * @property nextTrainedDate The date when this node should be trained next.
 */
@Entity(
  tableName = "NodeEntity",
  indices =
    [
      Index(value = ["nextTrainedDate"]),
      Index(value = ["lastTrainedDate"]),
      Index(value = ["depth"]),
      Index(value = ["isDeleted"]),
      Index(value = ["updatedAt"]),
    ],
)
data class NodeEntity(

  /**
   * FEN string uniquely identifying the chess position. Used as the primary key.
   *
   * Note that it should not be always the exact FEN string: it does not keep useless en passant
   * information.
   */
  @PrimaryKey(autoGenerate = false) val fenRepresentation: String,

  /** The date when this node was last trained */
  val lastTrainedDate: LocalDate = DateUtil.today(),

  /** The date when this node should be trained next */
  val nextTrainedDate: LocalDate = DateUtil.today(),

  /**
   * Depth of the node. Theoretically, a node can have many possible depth. Only the minimum one
   * should be stored.
   */
  val depth: Int,

  /** If true, the node is deleted. */
  val isDeleted: Boolean = false,

  /** The date time of the last update. */
  val updatedAt: LocalDateTime = DateUtil.now(),
) {
  fun toUnlinkedStoredNode(): UnlinkedStoredNode {
    return UnlinkedStoredNode(
      PositionIdentifier(fenRepresentation),
      PreviousAndNextDate(lastTrainedDate, nextTrainedDate),
      depth,
      isDeleted,
      updatedAt,
    )
  }

  init {
    check(lastTrainedDate <= nextTrainedDate) {
      "Last trained date cannot be after next trained date"
    }
  }
}
