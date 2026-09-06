package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

/**
 * Data class representing one edge to repertoire tag row in the database. Many to many: the same
 * edge (identified by [origin]/[destination]) can have more than one live row, one per repertoire
 * it belongs to.
 *
 * @property origin Origin position of the tagged move.
 * @property destination Destination position of the tagged move.
 * @property repertoireId The repertoire this edge belongs to.
 * @property isDeleted Whether this tag has been removed.
 * @property updatedAt Date at which this row was last updated.
 * @property originDevice Device that wrote this version. Stamped by
 *   [proj.memorchess.axl.core.graph.TreeStore].
 * @property deviceSeq That device's write counter at the time.
 */
data class DataEdgeRepertoireTag(
  val origin: PositionKey,
  val destination: PositionKey,
  val repertoireId: String,
  val isDeleted: Boolean = false,
  val updatedAt: Instant = DateUtil.now(),
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
)
