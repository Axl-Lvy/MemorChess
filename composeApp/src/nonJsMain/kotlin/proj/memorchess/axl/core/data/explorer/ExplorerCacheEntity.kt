package proj.memorchess.axl.core.data.explorer

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

/** Room row for a cached Lichess explorer response. */
@Entity(tableName = "ExplorerCacheEntity")
data class ExplorerCacheEntity(
  @PrimaryKey val key: String,
  val json: String,
  val fetchedAt: Instant,
) {
  companion object {
    /** Composite primary key shaped as `${source.path}:${fen}`. */
    fun key(fen: String, source: ExplorerSource): String = "${source.path}:$fen"
  }
}
