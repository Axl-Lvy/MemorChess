package proj.memorchess.axl.core.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Move that can be stored in [ICommonDatabase] */
data class StoredMove(
  /** Origin position of the move */
  val origin: PositionKey,

  /** Destination position of the move */
  val destination: PositionKey,

  /** The move in standard notation */
  val move: String,

  /**
   * Whether the move has to be learned.
   *
   * A bad move is a mistake. It is still saved because the user has to learn how to counter it.
   *
   * Bad moves are always isolated: previous and the next moves are good.
   */
  var isGood: Boolean? = null,

  /** The date when this move was last trained */
  var lastTrainedDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),

  /** The date when this move should be trained next */
  var nextTrainedDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) {
  /** Saves the move to the database */
  suspend fun save() {
    DatabaseHolder.getDatabase().insertMove(this)
  }
}
