package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

/**
 * One edge to repertoire tag row; an edge has one live row per repertoire it belongs to.
 *
 * @property move The tagged move in SAN.
 * @property isGood The author's marking for a tag written by an install, which makes the repertoire
 *   a holder of the move, or `null` for a tag that only records membership.
 * @property deviceSeq [originDevice]'s write counter when it wrote this version.
 */
data class DataEdgeRepertoireTag(
  val origin: PositionKey,
  val destination: PositionKey,
  val repertoireId: String,
  val move: String,
  val isGood: Boolean? = null,
  val isDeleted: Boolean = false,
  val updatedAt: Instant = DateUtil.now(),
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
)
